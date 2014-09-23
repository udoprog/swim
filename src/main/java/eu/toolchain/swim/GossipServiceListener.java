package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.Task;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.Gossip;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingReq;
import eu.toolchain.swim.serializers.AckSerializer;
import eu.toolchain.swim.serializers.PingReqSerializer;
import eu.toolchain.swim.serializers.PingSerializer;
import eu.toolchain.swim.serializers.Serializer;

@Slf4j
@Data
public class GossipServiceListener {
    private static final byte PING = 0;
    private static final byte ACK = 1;
    private static final byte PINGREQ = 2;

    private final EventLoop loop;
    private final DatagramBindChannel channel;
    private final List<InetSocketAddress> seeds;
    private final Provider<Boolean> alive;
    private final Random random;

    private final Map<InetSocketAddress, NodeData> nodes = new ConcurrentHashMap<>();
    private final Set<Gossip> gossip = new HashSet<>();

    /**
     * Maintained lists of random pools to make sure entries are fetched uniformly randomly.
     */
    private final Map<NodeFilter, Queue<NodeData>> randomPools = new HashMap<>();

    /* pending pings */
    private Map<UUID, PendingOperation> pending = new HashMap<>();

    private final long pendingThreshold = 2000;
    private final long payloadLimit = 20;

    // how many times we need to have gossiped about a node before we remove it.
    private long inc = 0;

    private final ByteBuffer output = ByteBuffer.allocate(0xffff);

    public void start() {
        for (InetSocketAddress seed : seeds)
            nodes.put(seed, new NodeData(loop, seed));

        pingRandom();

        loop.schedule(2000, new Task() {
            @Override
            public void run() throws Exception {
                expireOperations(loop.now());
                pingRandom();
                loop.schedule(2000, this);
            }
        });
    }

    void read(InetSocketAddress source, ByteBuffer input) throws Exception {
        final byte type = input.get();

        final Collection<Gossip> payloads = receive(source, input, type);

        handleGossip(payloads);
    }

    private void pingRandom() {
        final Collection<NodeData> peers = randomPeers(10, NodeFilters.any());

        for (NodeData node : peers) {
            try {
                sendPing(node.getAddress());
            } catch (Exception e) {
                log.error("failed to send ping: " + node, e);
            }
        }
    }

    private void expireOperations(final long now) throws Exception {
        final Set<UUID> expired = new HashSet<>();

        for (final Map.Entry<UUID, PendingOperation> entry : pending.entrySet()) {
            final UUID id = entry.getKey();
            final PendingOperation op = entry.getValue();

            if ((op.getStarted() + pendingThreshold) <= now)
                expired.add(id);
        }

        for (final UUID id : expired) {
            final PendingOperation expire = pending.remove(id);

            log.debug("{}: EXPIRE: {}", loop.now(), expire);

            if (expire instanceof PendingPing) {
                final PendingPing p = (PendingPing) expire;

                final NodeData data = this.nodes.get(p.getTarget());

                if (data == null) {
                    log.warn("No such target: {}", p.getTarget());
                    continue;
                }

                this.nodes.put(p.getTarget(), data.state(NodeState.SUSPECT));
            }
        }
    }

    private Collection<Gossip> receive(InetSocketAddress source,
            final ByteBuffer input, byte type) throws Exception {
        switch (type) {
        case PING:
            final Ping ping = PingSerializer.get().deserialize(input);
            handlePing(source, ping);
            return ping.getPayloads();
        case ACK:
            final Ack ack = AckSerializer.get().deserialize(input);
            handleAck(source, ack);
            return ack.getPayloads();
        case PINGREQ:
            final PingReq pingReq = PingReqSerializer.get().deserialize(input);
            handlePingReq(source, pingReq);
            return pingReq.getPayloads();
        default:
            throw new RuntimeException("Unsupported message type: "
                    + Integer.toHexString(0xff & type));
        }
    }

    /* handlers */

    private void handleGossip(Collection<Gossip> payloads) throws Exception {
        for (final Gossip g : payloads) {
            if (g.getAbout().equals(channel.getBindAddress())) {
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

    private NodeData checkState(final NodeData data, final NodeState state,
            final long inc) {
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
        default:
            return null;
        }
    }

    private void handlePingReq(InetSocketAddress source, PingReq pingReq)
            throws Exception {
        log.debug("{}: PING+REQ: {}", loop.now(), pingReq);

        final UUID id = pingReq.getId();
        final InetSocketAddress target = pingReq.getTarget();
        sendPingFromRequest(target, source, id);
    }

    /**
     * Handle a received acknowledgement.
     */
    private void handleAck(SocketAddress source, Ack ack) throws Exception {
        log.debug("{}: ACK: {}", loop.now(), ack);

        final Object any = pending.remove(ack.getId());

        if (any == null) {
            log.warn("Received ACK for non-pending message: {}", ack);
            return;
        }

        /*
         * Requests which have been performed from this node.
         */
        if (any instanceof PendingPing) {
            final PendingPing p = (PendingPing) any;
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
            final PendingPingReq p = (PendingPingReq) any;
            // send back acknowledgement to the source peer.
            sendAck(p.getSource(), p.getPingId(), ack.isAlive());
            return;
        }
    }

    private void handlePing(final InetSocketAddress source, final Ping ping)
            throws Exception {
        log.debug("{}: PING: {}", loop.now(), ping);

        sendAck(source, ping.getId(), alive.get());
    }

    /* senders */

    private void sendAck(final InetSocketAddress target, final UUID id,
            boolean alive) throws Exception {
        send(ACK, target, new Ack(id, alive, buildPayloads()),
                AckSerializer.get());
    }

    private void sendPingRequest(final InetSocketAddress target,
            final InetSocketAddress peer) throws Exception {
        final UUID id = UUID.randomUUID();
        send(PINGREQ, peer, new PingReq(id, target, buildPayloads()),
                PingReqSerializer.get());
        pending.put(id, new PendingPing(loop.now(), target,
                true));
    }

    private void sendPingFromRequest(final InetSocketAddress target,
            final InetSocketAddress source, final UUID pingId) throws Exception {
        final UUID id = UUID.randomUUID();
        send(PING, target, new Ping(id, buildPayloads()), PingSerializer.get());
        pending.put(id, new PendingPingReq(loop.now(), target,
                pingId, source));
    }

    private void sendPing(final InetSocketAddress target) throws Exception {
        final UUID id = UUID.randomUUID();
        send(PING, target, new Ping(id, buildPayloads()), PingSerializer.get());
        pending.put(id, new PendingPing(loop.now(), target,
                false));
    }

    private Collection<Gossip> buildPayloads() throws Exception {
        final Set<Gossip> result = new HashSet<>();
        return result;
    }

    private Collection<NodeData> randomPeers(long k, NodeFilter filter) {
        final ArrayList<NodeData> result = new ArrayList<>();

        Queue<NodeData> nodes = randomPools.get(filter);

        while (result.size() < k) {
            if (nodes != null) {
                while (!nodes.isEmpty())
                    result.add(nodes.poll());
            }

            final List<NodeData> source = peers(filter);
            Collections.shuffle(source, random);
            nodes = new LinkedList<>(source);
        }

        if (nodes != null && !nodes.isEmpty())
            randomPools.put(filter, nodes);

        return new HashSet<>(result);
    }

    private List<NodeData> peers(NodeFilter filter) {
        final List<NodeData> result = new ArrayList<>();

        for (final NodeData p : this.nodes.values()) {
            if (filter.matches(p))
                result.add(p);
        }

        return result;
    }

    private <T> void send(byte type, InetSocketAddress target, T data,
            Serializer<T> serializer) throws Exception {
        output.clear();
        output.put(type);
        serializer.serialize(output, data);
        output.flip();

        channel.send(target, output);
    }
}
