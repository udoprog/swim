package eu.toolchain.swim;

import static eu.toolchain.swim.NodeFilters.address;
import static eu.toolchain.swim.NodeFilters.and;
import static eu.toolchain.swim.NodeFilters.any;
import static eu.toolchain.swim.NodeFilters.not;
import static eu.toolchain.swim.NodeFilters.or;
import static eu.toolchain.swim.NodeFilters.state;
import static eu.toolchain.swim.NodeFilters.younger;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.serializer.Serializer;
import eu.toolchain.serializer.io.ByteBufferSerialReader;
import eu.toolchain.serializer.io.ByteBufferSerialWriter;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.Task;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.Gossip;
import eu.toolchain.swim.messages.Message;
import eu.toolchain.swim.messages.MyStateGossip;
import eu.toolchain.swim.messages.OtherStateGossip;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingReq;
import eu.toolchain.swim.messages.Serializers;

@Slf4j
@Data
public class GossipServiceListener {
    private static final NodeFilter ALIVE_OR_SUSPECT = or(NodeFilters.state(NodeState.SUSPECT), state(NodeState.ALIVE));

    private static final long DEFAULT_EXPIRE_TIMER = 1000;
    private static final long PEER_TIMEOUT = 30000;
    private static final long DEFAULT_PING_TIMEOUT = 2000;
    private static final long DEFAULT_PING_REQ_TIMEOUT = 1000;
    private static final long DEFAULT_GOSSIP_LIMIT = 20;

    private final EventLoop loop;
    private final DatagramBindChannel channel;
    private final List<InetSocketAddress> seeds;
    private final Provider<Boolean> alive;
    private final Random random;

    private final Serializers s = new Serializers();
    private final Serializer<Message> message = s.message();

    private final Map<InetSocketAddress, Peer> peers = new ConcurrentHashMap<>();
    private final AtomicReference<Peer> local = new AtomicReference<>();

    private final long expireTimer = DEFAULT_EXPIRE_TIMER;
    private final long peerTimeout = PEER_TIMEOUT;
    private final long pingTimeout = DEFAULT_PING_TIMEOUT;
    private final long pingReqTimeout = DEFAULT_PING_REQ_TIMEOUT;
    private final long gossipLimit = DEFAULT_GOSSIP_LIMIT;

    /**
     * Maintained lists of random pools to make sure entries are fetched uniformly randomly.
     */
    private final Map<NodeFilter, Queue<InetSocketAddress>> randomPools = new HashMap<>();

    /* pending pings */
    private Map<UUID, PendingOperation> pending = new HashMap<>();

    private final ByteBuffer output = ByteBuffer.allocate(0xffff);

    public List<InetSocketAddress> members() {
        final List<InetSocketAddress> members = new ArrayList<>();

        for (final Peer data : peers.values()) {
            if (data.isAlive())
                members.add(data.getAddress());
        }

        return members;
    }

    public void start() {
        for (InetSocketAddress seed : seeds)
            peers.put(seed, new Peer(seed, loop.now()));

        // local node
        local.set(new Peer(channel.getBindAddress(), NodeState.ALIVE, 1, loop.now()));

        loop.schedule(0, new Task() {
            @Override
            public void run() throws Exception {
                expireOperations(loop.now());

                final Collection<Peer> peers = randomPeers(10, any());

                for (Peer node : peers) {
                    try {
                        ping(node.getAddress());
                    } catch (Exception e) {
                        log.error("failed to send ping: " + node, e);
                    }
                }

                loop.schedule(expireTimer, this);
            }
        });
    }

    void read(InetSocketAddress source, ByteBuffer input) throws Exception {
        handleGossip(receive(source, input));
    }

    private void expireOperations(final long now) throws Exception {
        final ArrayList<UUID> expired = new ArrayList<>();

        for (final Map.Entry<UUID, PendingOperation> entry : pending.entrySet()) {
            final UUID id = entry.getKey();
            final PendingOperation op = entry.getValue();

            if (op.getExpires() <= now)
                expired.add(id);
        }

        for (final UUID id : expired) {
            final PendingOperation expire = pending.remove(id);

            log.debug("{}: EXPIRE: {}", loop.now(), expire);

            if (expire instanceof PendingPing) {
                expirePendingPing((PendingPing) expire);
                continue;
            }

            if (expire instanceof PendingPingReq) {
                expirePendingPingReq((PendingPingReq) expire);
                continue;
            }
        }

        for (final Peer p : new ArrayList<>(peers.values())) {
            // don't remove seed nodes
            if (p.isSeed())
                continue;

            // is timeout in the future?
            if (p.getUpdated() + peerTimeout > now)
                continue;

            log.warn("{}:{}: Removing peer {}, haven't seen them for {} seconds", channel.getBindAddress(), now, p,
                    TimeUnit.SECONDS.convert(peerTimeout, TimeUnit.MILLISECONDS));
            peers.remove(p.getAddress());
        }
    }

    private void expirePendingPingReq(PendingPingReq expired) throws Exception {
        final Peer data = this.peers.get(expired.getTarget());

        if (data == null) {
            log.warn("No such target: {}", expired.getTarget());
            return;
        }

        peers.put(expired.getTarget(), data.update(NodeState.SUSPECT, loop.now()));
        ack(expired.getSource(), expired.getPingId(), false);
    }

    private void expirePendingPing(PendingPing expired) throws Exception {
        final Peer data = this.peers.get(expired.getTarget());

        if (data == null) {
            log.warn("No such target: {}", expired.getTarget());
            return;
        }

        // select a random peer, that is _not_ the just recently pinged address.
        final Peer node = randomPeers(1, not(address(expired.getTarget()))).iterator().next();

        pingRequest(node.getAddress(), expired.getTarget());
    }

    private Collection<Gossip> receive(InetSocketAddress source, final ByteBuffer input) throws Exception {
        final ByteBufferSerialReader reader = new ByteBufferSerialReader(input);

        final Message message = this.message.deserialize(reader);

        if (message instanceof Ping) {
            handlePing(source, (Ping) message);
        } else if (message instanceof Ack) {
            handleAck(source, (Ack) message);
        } else if (message instanceof PingReq) {
            handlePingReq(source, (PingReq) message);
        } else {
            throw new IllegalArgumentException("Invalid message: " + message);
        }

        final Peer n = peers.get(source);

        if (n != null)
            peers.put(source, n.poke(loop.now()));

        return message.getGossip();
    }

    /* handlers */

    private void handleGossip(Collection<Gossip> payloads) throws Exception {
        for (final Gossip g : payloads) {
            if (g instanceof OtherStateGossip) {
                handleOtherStateGossip((OtherStateGossip) g);
                continue;
            }

            if (g instanceof MyStateGossip) {
                handleMyStateGossip(g);
                continue;
            }
        }
    }

    private void handleOtherStateGossip(OtherStateGossip g) {
        final long now = loop.now();

        Peer about = peers.get(g.getAbout());

        if (about == null)
            about = new Peer(g.getAbout(), g.getState(), g.getInc(), now);

        // something fresher than we know.
        // since this is an incremental update, we know that it has to originate
        // from the node in question, or someone who has a more recent (direct) picture with that node.
        if (about.getInc() < g.getInc()) {
            about = about.update(g.getState(), g.getInc(), now);
        } else {
            about = about.poke(now);
        }

        about.rumor(g.getSource(), now, g.getInc(), g.getState());
        peers.put(about.getAddress(), about);
    }

    private void handleMyStateGossip(Gossip g) {
        final long now = loop.now();

        Peer about = peers.get(g.getAbout());

        if (about == null)
            about = new Peer(g.getAbout(), g.getState(), g.getInc(), now);

        about = about.update(g.getState(), g.getInc(), now);

        // something fresher than we know.
        // since this is an incremental update, we know that it has to originate
        // from the node in question, or someone who has a more recent (direct) picture with that node.
        peers.put(about.getAddress(), about);
    }

    private void handlePingReq(InetSocketAddress source, PingReq pingReq) throws Exception {
        log.debug("{}: PING+REQ: {}", loop.now(), pingReq);
        final UUID id = loop.uuid();
        send(pingReq.getTarget(), new Ping(id, buildGossip()));
        final long now = loop.now();
        pending.put(id, new PendingPingReq(now, now + pingReqTimeout, pingReq.getTarget(), pingReq.getId(), source));
    }

    /**
     * Handle a received acknowledgement.
     */
    private void handleAck(SocketAddress source, Ack ack) throws Exception {
        log.debug("{}: ACK: {}", loop.now(), ack);

        final PendingOperation any = pending.remove(ack.getId());

        if (any == null) {
            log.warn("Received ACK for non-pending message: {}", ack);
            return;
        }

        /* Requests which have been performed from this node. */
        if (any instanceof PendingPing) {
            final PendingPing p = (PendingPing) any;
            final Peer data = peers.get(p.getTarget());

            if (data == null) {
                log.warn("No node for ack: {}", p);
                return;
            }

            if (ack.isOk()) {
                peers.put(p.getTarget(), data.update(NodeState.ALIVE, loop.now()));
            } else {
                peers.put(p.getTarget(), data.update(NodeState.SUSPECT, loop.now()));
            }

            return;
        }

        /* Requests which have been performed on behalf of another node. */
        if (any instanceof PendingPingReq) {
            final PendingPingReq p = (PendingPingReq) any;
            // send back acknowledgement to the source peer.
            ack(p.getSource(), p.getPingId(), true);
            return;
        }
    }

    private void handlePing(final InetSocketAddress source, final Ping ping) throws Exception {
        log.debug("{}: PING: {}", loop.now(), ping);
        ack(source, ping.getId(), alive.get());
    }

    /* senders */

    private void ack(final InetSocketAddress target, final UUID id, final boolean ok) throws Exception {
        send(target, new Ack(id, ok, buildGossip()));
    }

    private void pingRequest(final InetSocketAddress peer, final InetSocketAddress target) throws Exception {
        final UUID id = loop.uuid();
        final long now = loop.now();
        pending.put(id, new PendingPing(now, now + pingTimeout, target, true));
        send(peer, new PingReq(id, target, buildGossip()));
    }

    private void ping(final InetSocketAddress target) throws Exception {
        final UUID id = loop.uuid();
        final long now = loop.now();
        pending.put(id, new PendingPing(now, now + pingTimeout, target, false));
        send(target, new Ping(id, buildGossip()));
    }

    private List<Gossip> buildGossip() throws Exception {
        final List<Gossip> result = new ArrayList<>();

        result.add(myState());
        result.addAll(otherStates());

        return result;
    }

    private List<Gossip> otherStates() {
        final List<Gossip> result = new ArrayList<>();

        final InetSocketAddress address = channel.getBindAddress();

        final NodeFilter filter = and(ALIVE_OR_SUSPECT, younger(30000));

        for (final Peer v : randomPeers(gossipLimit, filter))
            result.add(new OtherStateGossip(address, v.getAddress(), v.getState(), v.getInc()));

        return result;
    }

    private MyStateGossip myState() {
        Peer me = local.get();

        if (me == null)
            throw new IllegalStateException("information about self should never dissapear");

        Peer external = peers.get(me.getAddress());

        final NodeState actual = alive.get() ? NodeState.ALIVE : NodeState.DEAD;

        // has our state changed
        if (me.getState() != actual || isBadRumorSpreading(me, external, actual)) {
            me = me.update(actual, me.getInc() + 1, loop.now());
            local.set(me);
        }

        return new MyStateGossip(me.getAddress(), me.getState(), me.getInc());
    }

    private boolean isBadRumorSpreading(Peer node, Peer external, final NodeState actual) {
        // no opinion
        if (external == null)
            return false;

        // external opinion based on outdated inc.
        if (external.getInc() < node.getInc())
            return false;

        // external does not match.
        return external.getState() != actual;
    }

    private Collection<Peer> randomPeers(long k, NodeFilter filter) {
        final ArrayList<Peer> result = new ArrayList<>();

        Queue<InetSocketAddress> nodes = randomPools.get(filter);

        while (result.size() < k) {
            if (nodes != null) {
                while (!nodes.isEmpty()) {
                    final InetSocketAddress addr = nodes.poll();

                    final Peer p = peers.get(addr);

                    if (p == null)
                        continue;

                    if (!filter.matches(loop, p))
                        continue;

                    result.add(p);
                }
            }

            final List<InetSocketAddress> source = peers(filter);

            if (source.isEmpty())
                return result;

            Collections.shuffle(source, random);
            nodes = new LinkedList<>(source);
        }

        if (nodes != null && !nodes.isEmpty())
            randomPools.put(filter, nodes);

        return new HashSet<>(result);
    }

    private List<InetSocketAddress> peers(NodeFilter filter) {
        final List<InetSocketAddress> result = new ArrayList<>();

        for (final Peer p : this.peers.values()) {
            if (filter.matches(loop, p))
                result.add(p.getAddress());
        }

        return result;
    }

    private void send(InetSocketAddress target, Message data) throws Exception {
        final ByteBufferSerialWriter output = new ByteBufferSerialWriter();
        message.serialize(output, data);
        channel.send(target, output.buffer());
    }
}
