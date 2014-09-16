package eu.toolchain.swim;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.Gossip;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingReq;
import eu.toolchain.swim.serializers.AckSerializer;
import eu.toolchain.swim.serializers.PingReqSerializer;
import eu.toolchain.swim.serializers.PingSerializer;
import eu.toolchain.swim.serializers.Serializer;

@Slf4j
public class GossipService {
    private static final byte PING = 0;
    private static final byte ACK = 1;
    private static final byte PINGREQ = 2;

    private final List<Node> seeds;
    private final Map<InetSocketAddress, NodeData> nodes = new ConcurrentHashMap<>();

    private final InetSocketAddress localAddress;
    private final Provider<Boolean> alive;
    private final Selector selector;
    private final DatagramChannel channel;
    private final Random random = new Random();

    private final long pendingThreshold = 2000;
    private final long payloadLimit = 20;
    // how many times we need to have gossiped about a node before we remove it.
    private long inc = 0;

    public GossipService(final InetSocketAddress address, final List<Node> seeds, final Provider<Boolean> alive) throws IOException {
        this.seeds = new ArrayList<>(seeds);
        this.localAddress = address;
        this.alive = alive;
        this.selector = Selector.open();
        this.channel = DatagramChannel.open();
    }

    private final ByteBuffer input = ByteBuffer.allocate(0xffff);
    private final ByteBuffer output = ByteBuffer.allocate(0xffff);

    private final Set<Gossip> gossip = new HashSet<>();

    /* pending pings */
    private Map<UUID, PendingOperation> pending = new HashMap<>();

    private final Scheduler scheduler = new Scheduler(10);

    public void run() throws Exception {
        for (final Node n : seeds)
            this.nodes.put(n.getAddress(), new NodeData(n));

        channel.socket().bind(localAddress);
        channel.configureBlocking(false);

        channel.register(selector, SelectionKey.OP_READ);

        log.info("Starting server {}", localAddress);

        final Task expire = new Task() {
            @Override
            public void run(Scheduler.Session session) {
                log.info("EXPIRE!");
                scheduler.schedule(2000, this);
            }
        };

        final Task expire2 = new Task() {
            @Override
            public void run(Scheduler.Session session) {
                log.info("EXPIRE 2!");
                scheduler.schedule(4000, this);
            }
        };

        scheduler.schedule(2000, expire);
        scheduler.schedule(4000, expire2);

        while (true) {
            final Set<SelectionKey> selected = select();

            if (selected == null)
                break;

            for (SelectionKey key : selected) {
                if (!key.isValid())
                    continue;

                if (key.isReadable()) {
                    read(key);
                    continue;
                }
            }

            selected.clear();
        }

        log.info("Shutting down");
    }

    private Set<SelectionKey> select() throws IOException {
        while (true) {
            final long now = System.currentTimeMillis();

            final Scheduler.Session session = scheduler.next(now);

            final long sleep;

            if (session != null) {
                sleep = Math.max(0, session.getWhen() - now);

                if (sleep == 0) {
                    session.execute();
                    continue;
                }
            } else {
                sleep = 0;
            }

            int keys = selector.select(sleep);

            // timed out.
            if (keys == 0) {
                session.execute();
                continue;
            }

            final Set<SelectionKey> selected = selector.selectedKeys();

            if (selected.isEmpty())
                continue;

            return selected;
        }
    }

    private void expireOperations(final long now) throws Exception {
        final Set<UUID> expired = new HashSet<>();

        for (Map.Entry<UUID, PendingOperation> entry : pending.entrySet()) {
            final UUID id = entry.getKey();
            final PendingOperation op = entry.getValue();

            if ((op.getStarted() + pendingThreshold) < now)
                expired.add(id);
        }

        for (final UUID id : expired) {
            final PendingOperation expire = pending.remove(id);

            if (expire instanceof PendingPing) {
                final PendingPing p = (PendingPing)expire;

                final NodeData data = this.nodes.get(p.getTarget());

                if (data == null) {
                    log.warn("No such target: {}", p.getTarget());
                    continue;
                }

                this.nodes.put(p.getTarget(), data.state(NodeState.CONFIRM));
            }
        }
    }

    private void read(SelectionKey key) throws Exception {
        DatagramChannel c = (DatagramChannel)key.channel();

        while (true) {
            input.clear();

            final InetSocketAddress s = (InetSocketAddress)c.receive(input);

            if (s == null)
                break;

            input.flip();

            byte type = input.get();

            final Collection<Gossip> payloads = receive(c, s, type);

            handleGossip(payloads);
        }
    }

    private Collection<Gossip> receive(DatagramChannel c,
            final InetSocketAddress s, byte type) throws Exception {
        switch (type) {
        case PING:
            final Ping ping = PingSerializer.get().deserialize(input);
            handlePing(c, s, ping);
            return ping.getPayloads();
        case ACK:
            final Ack ack = AckSerializer.get().deserialize(input);
            handleAck(c, s, ack);
            return ack.getPayloads();
        case PINGREQ:
            final PingReq pingReq = PingReqSerializer.get().deserialize(input);
            handlePingReq(c, s, pingReq);
            return pingReq.getPayloads();
        default:
            throw new RuntimeException("Unsupported message type: " + Integer.toHexString(0xff & type));
        }
    }

    /* handlers */

    private void handleGossip(Collection<Gossip> payloads) throws Exception {
        for (final Gossip g : payloads) {
            if (g.getAbout().equals(localAddress)) {
                // TODO: handle
                continue;
            }

            final NodeData l = nodes.get(g.getAbout());

            if (l == null)
                continue;

            final NodeData update = checkState(l, g.getState(), g.getInc());

            if (update != null)
                nodes.put(g.getAbout(), update);
        }
    }

    private NodeData checkState(final NodeData data, final NodeState state, final long inc) {
        switch (state) {
        case ALIVE:
            if (data.getState() == NodeState.SUSPECT && inc > data.getInc())
                return data.state(NodeState.ALIVE).inc(inc);

            if (data.getState() == NodeState.ALIVE && inc > data.getInc())
                return data.state(NodeState.ALIVE).inc(inc);

            return null;
        case SUSPECT:
            if (data.getState() == NodeState.SUSPECT && inc > data.getInc())
                return data.state(NodeState.SUSPECT).inc(inc);

            if (data.getState() == NodeState.ALIVE && inc >= data.getInc())
                return data.state(NodeState.SUSPECT).inc(inc);

            return null;
        case CONFIRM:
            return data.state(NodeState.CONFIRM);
        default:
            return null;
        }
    }

    private void handlePingReq(DatagramChannel c, InetSocketAddress source,
            PingReq pingReq) throws Exception {
        log.debug("PING+REQ: {}", pingReq);
        final UUID id = pingReq.getId();
        final InetSocketAddress target = pingReq.getTarget();
        sendPingFromRequest(c, target, source, id);
    }

    /**
     * Handle a received acknowledgement.
     */
    private void handleAck(DatagramChannel c, SocketAddress s, Ack ack) throws Exception {
        log.debug("ACK: {}", ack);
        final Object any = pending.remove(ack.getId());

        if (any == null) {
            log.warn("Received ACK for non-pending message: {}", ack);
            return;
        }

        /*
         * Requests which have been performed from this node.
         */
        if (any instanceof PendingPing) {
            final PendingPing p = (PendingPing)any;
            final NodeData data = nodes.get(p.getTarget());

            if (data == null) {
                log.warn("No node for ack: {}", p);
                return;
            }

            nodes.put(p.getTarget(), data.state(ack.toNodeState()));
            return;
        }

        /*
         * Requests which have been performed on behalf of another node.
         */
        if (any instanceof PendingPingReq) {
            final PendingPingReq p = (PendingPingReq)any;
            // send back acknowledgement to the source peer.
            sendAck(c, p.getSource(), p.getPingId(), ack.isAlive());
            return;
        }
    }

    private void handlePing(final DatagramChannel c, final InetSocketAddress s, final Ping ping) throws Exception {
        log.debug("PING: {}", ping);
        sendAck(c, s, ping.getId(), alive.get());
    }

    /* senders */

    private void sendAck(final DatagramChannel c, final InetSocketAddress target,
            final UUID id, boolean alive) throws Exception {
        send(ACK, c, target, new Ack(id, alive, buildPayloads()), AckSerializer.get());
    }

    private void sendPingRequest(final DatagramChannel c, final InetSocketAddress target, final InetSocketAddress peer) throws Exception {
        final UUID id = UUID.randomUUID();
        send(PINGREQ, channel, peer, new PingReq(id, target, buildPayloads()), PingReqSerializer.get());
        pending.put(id, new PendingPing(System.currentTimeMillis(), target, true));
    }

    private void sendPingFromRequest(final DatagramChannel c, final InetSocketAddress target, final InetSocketAddress source, final UUID pingId) throws Exception {
        final UUID id = UUID.randomUUID();
        send(PING, channel, target, new Ping(id, buildPayloads()), PingSerializer.get());
        pending.put(id, new PendingPingReq(System.currentTimeMillis(), target, pingId, source));
    }

    private void sendPing(final DatagramChannel c, final InetSocketAddress target) throws Exception {
        final UUID id = UUID.randomUUID();
        send(PING, channel, target, new Ping(id, buildPayloads()), PingSerializer.get());
        pending.put(id, new PendingPing(System.currentTimeMillis(), target, false));
    }

    private Collection<Gossip> buildPayloads() throws Exception {
        final Set<Gossip> result = new HashSet<>();
        return result;
    }

    private Collection<NodeData> randomPeers(long k, NodeFilter filter) {
        final List<NodeData> source = peers(filter);
        final ArrayList<NodeData> peers = new ArrayList<>(source);

        final Set<NodeData> targets = new HashSet<>();

        if (peers.isEmpty())
            return targets;

        for (int i = 0; i < k; i++)
            targets.add(peers.get(random.nextInt(peers.size())));

        return targets;
    }

    private List<NodeData> peers(NodeFilter filter) {
        final SortedSet<NodeData> result = new TreeSet<>();

        for (final NodeData p : this.nodes.values()) {
            if (filter.matches(p))
                result.add(p);
        }

        return new ArrayList<>(result);
    }

    private <T> void send(byte type, DatagramChannel c, InetSocketAddress to, T data,
            Serializer<T> serializer) throws Exception {
        output.clear();
        output.put(type);
        serializer.serialize(output, data);
        output.flip();
        c.send(output, to);
    }
}
