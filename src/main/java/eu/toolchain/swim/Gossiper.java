package eu.toolchain.swim;

import static eu.toolchain.swim.NodeFilters.address;
import static eu.toolchain.swim.NodeFilters.id;
import static eu.toolchain.swim.NodeFilters.not;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.async.ResolvableFuture;
import eu.toolchain.serializer.Serializer;
import eu.toolchain.serializer.io.ByteBufferSerialReader;
import eu.toolchain.serializer.io.ByteBufferSerialWriter;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;
import eu.toolchain.swim.async.Task;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.DirectGossip;
import eu.toolchain.swim.messages.Gossip;
import eu.toolchain.swim.messages.Message;
import eu.toolchain.swim.messages.OtherGossip;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingRequest;
import eu.toolchain.swim.messages.Serializers;
import eu.toolchain.swim.statistics.NoopReporter;
import eu.toolchain.swim.statistics.Reporter;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = { "localId" })
public class Gossiper implements DatagramBindListener<Gossiper> {
    private static final long DEFAULT_EXPIRE_TIMER = 1000;
    private static final long DEFAULT_SEED_TIMER = 10000;
    private static final long PEER_TIMEOUT = 30000;
    private static final long DEFAULT_PING_TIMEOUT = 2000;
    private static final long DEFAULT_PING_REQ_TIMEOUT = 1500;
    private static final long DEFAULT_PING_REQ_DELEGATE_TIMEOUT = 1000;
    private static final long DEFAULT_GOSSIP_LIMIT = 20;
    private static final long DEFAULT_CONFIRM_DELAY = 5000;

    private final UUID localId;
    private final EventLoop loop;
    private final List<InetSocketAddress> seeds;
    private final Provider<Boolean> alive;
    private final Random random;
    private final Reporter reporter;
    private final ChangeListener listener;

    private final Serializers s = new Serializers();
    private final Serializer<Message> message = s.message();

    private final Map<UUID, Peer> peers = new ConcurrentHashMap<>();
    private final AtomicLong inc = new AtomicLong();
    private final AtomicReference<Set<UUID>> alivePeers = new AtomicReference<>();
    private final AtomicBoolean previous = new AtomicBoolean(true);

    /**
     * Maintained lists of random pools to make sure entries are fetched uniformly randomly.
     */
    private final Map<NodeFilter, Queue<InetSocketAddress>> randomPools = new HashMap<>();

    private final long expireTimer = DEFAULT_EXPIRE_TIMER;
    private final long seedTimer = DEFAULT_SEED_TIMER;
    private final long peerTimeout = PEER_TIMEOUT;
    private final long pingTimeout = DEFAULT_PING_TIMEOUT;
    private final long pingRequestTimeout = DEFAULT_PING_REQ_TIMEOUT;
    private final long pingRequestDelegateTimeout = DEFAULT_PING_REQ_DELEGATE_TIMEOUT;
    private final long gossipLimit = DEFAULT_GOSSIP_LIMIT;
    private final long confirmDelay = DEFAULT_CONFIRM_DELAY;

    public List<UUID> members() {
        final List<UUID> members = new ArrayList<>();

        if (alive.get())
            members.add(localId);

        for (final Peer peer : peers.values()) {
            if (peer.getState() != NodeState.CONFIRM)
                members.add(peer.getId());
        }

        return members;
    }

    @Override
    public Gossiper setup(final DatagramBindChannel channel) {
        final Channel session = new Channel(channel);

        channel.register(new ReceivePacket() {
            @Override
            public void packet(final InetSocketAddress source, final ByteBuffer packet) throws Exception {
                session.read(source, packet);
            }
        });

        loop.schedule(0, new Task() {

            @Override
            public void run() throws Exception {
                try {
                    expirePeers();
                } catch (Exception e) {
                    log.error("Peer expire failed", e);
                }

                try {
                    checkAlive();
                } catch (Exception e) {
                    log.error("failed to perform alive check", e);
                }

                loop.schedule(expireTimer, this);
            }

        });

        session.start();
        return this;
    }

    private void checkAlive() {
        final Set<UUID> members = new HashSet<>(members());
        final Set<UUID> alive = alivePeers.get();

        final Set<UUID> added;

        if (alive != null) {
            added = new HashSet<>(members);
            added.removeAll(alive);
        } else {
            added = members;
        }

        final Set<UUID> removed;

        if (alive != null) {
            removed = new HashSet<>(alive);
            removed.removeAll(members);
        } else {
            removed = new HashSet<>();
        }

        if (alive == null || !added.isEmpty() || !removed.isEmpty())
            alivePeers.set(members);

        for (final UUID add : added)
            listener.peerFound(this, add);

        for (final UUID remove : removed)
            listener.peerLost(this, remove);
    }

    private void expirePeers() throws Exception {
        final long now = loop.now();

        for (final Peer p : new ArrayList<>(peers.values())) {
            // is timeout in the future?
            if (p.getUpdated() + peerTimeout > now)
                continue;

            log.info("removed {}, missing for {} second(s)", p,
                    TimeUnit.SECONDS.convert(peerTimeout, TimeUnit.MILLISECONDS));

            peers.remove(p.getAddress());
        }
    }

    private Collection<Peer> randomPeers(long k, NodeFilter filter) {
        k = Math.min(k, peers.size());

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

        for (final Peer p : peers.values()) {
            if (filter.matches(loop, p))
                result.add(p.getAddress());
        }

        return result;
    }

    private DirectGossip myState() {
        final boolean alive = this.alive.get();

        final NodeState state = alive ? NodeState.ALIVE : NodeState.CONFIRM;

        // transition, time to inc
        if (alive != previous.get()) {
            inc.incrementAndGet();
            previous.set(alive);
        }

        return new DirectGossip(localId, state, inc.get());
    }

    @RequiredArgsConstructor
    public class Channel {
        private final DatagramBindChannel channel;

        /* pending pings */
        private Map<UUID, PendingOperation> pings = new HashMap<>();

        void read(InetSocketAddress source, ByteBuffer input) throws Exception {
            final Collection<Gossip> gossip = receive(source, input);
            handleGossip(source, gossip);
        }

        private void start() {
            loop.schedule(0, new Task() {
                @Override
                public void run() throws Exception {
                    try {
                        pingPeers();
                    } catch (Exception e) {
                        log.error("Peer pingin failed", e);
                    }

                    loop.schedule(expireTimer, this);
                }
            });

            loop.schedule(0, new Task() {
                @Override
                public void run() throws Exception {
                    try {
                        pingSeeds();
                    } catch (Exception e) {
                        log.error("Peer pingin failed", e);
                    }

                    loop.schedule(seedTimer, this);
                }
            });
        }

        /**
         * Ping seeds if they are _not_ part of the current list of nodes. This assert that the cluster (eventually)
         * converges.
         *
         * @throws Exception
         */
        private void pingSeeds() throws Exception {
            for (final InetSocketAddress seed : seeds) {
                if (!peers.containsKey(seed))
                    ping(seed);
            }
        }

        private void pingPeers() throws Exception {
            final Collection<Peer> peers = randomPeers(10, not(id(localId)));

            if (peers.isEmpty())
                System.out.println("no peers to ping...");

            for (Peer peer : peers) {
                System.out.println(localId + ": ping " + peer);
                ping(peer.getAddress());
            }
        }

        private ResolvableFuture<Void> expireIn(long delay, final UUID id) {
            return loop.schedule(delay, new Task() {
                @Override
                public void run() throws Exception {
                    final PendingOperation expire = pings.remove(id);

                    if (expire == null) {
                        log.error("pending operation not available: {}: {}", id, expire);
                        return;
                    }

                    reporter.reportExpire(id, expire);

                    final Peer peer = peers.get(expire.getTarget());

                    if (peer == null) {
                        reporter.reportMissingPeerForExpire(id, expire);
                        return;
                    }

                    if (expire instanceof PendingPingReq) {
                        final PendingPingReq pending = (PendingPingReq) expire;
                        ack(pending.getSource(), pending.getSourcePingId(), NodeState.SUSPECT, peer.getInc());
                        return;
                    }

                    final PendingPing pending = (PendingPing) expire;

                    // select a random peer, that is _not_ the just recently pinged address.
                    final Collection<Peer> nodes = randomPeers(1, not(address(pending.getTarget())));

                    // we are not connected with anyone :(
                    if (nodes.isEmpty())
                        return;

                    final Peer node = nodes.iterator().next();
                    pingRequest(node.getAddress(), pending.getTarget());
                }
            });
        }

        private ResolvableFuture<Void> confirmIn(long delay, final long updated, final InetSocketAddress address) {
            return loop.schedule(delay, new Task() {
                @Override
                public void run() throws Exception {
                    final Peer peer = peers.get(address);
                    peers.put(peer.getId(), peer.state(NodeState.CONFIRM));
                }
            });
        }

        private Collection<Gossip> receive(InetSocketAddress source, final ByteBuffer input) throws Exception {
            final ByteBufferSerialReader reader = new ByteBufferSerialReader(input);

            final Message message = Gossiper.this.message.deserialize(reader);

            if (message instanceof Ping) {
                handlePing(source, (Ping) message);
            } else if (message instanceof Ack) {
                handleAck(source, (Ack) message);
            } else if (message instanceof PingRequest) {
                handlePingRequest(source, (PingRequest) message);
            } else {
                throw new IllegalArgumentException("Invalid message: " + message);
            }

            final Peer n = peers.get(source);

            if (n != null)
                peers.put(n.getId(), n.touch(loop.now()));

            return message.getGossip();
        }

        /* handlers */

        private void handleGossip(InetSocketAddress source, Collection<Gossip> payloads) throws Exception {
            for (final Gossip g : payloads) {
                handleSingleGossip(source, g);
            }
        }

        private void handleSingleGossip(InetSocketAddress source, Gossip gossip) {
            // gossip about self
            if (gossip.getAbout().equals(localId)) {
                if (alive.get() && gossip.getState() != NodeState.ALIVE) {
                    inc.incrementAndGet();
                    return;
                }

                if (!alive.get() && gossip.getState() == NodeState.ALIVE) {
                    inc.incrementAndGet();
                    return;
                }

                return;
            }

            final long now = loop.now();

            Peer peer = peers.get(gossip.getAbout());

            if (peer == null) {
                if (gossip instanceof DirectGossip) {
                    final DirectGossip direct = (DirectGossip) gossip;

                    peer = new Peer(direct.getAbout(), source, direct.getState(), direct.getInc(), now, null);
                } else {
                    final OtherGossip other = (OtherGossip) gossip;

                    if (other.getState() != NodeState.ALIVE)
                        return;

                    peer = new Peer(other.getAbout(), other.getAboutAddress(), other.getState(), other.getInc(), now,
                            null);
                }

                peers.put(peer.getId(), peer);
                return;
            }

            if (gossip.getState() == NodeState.ALIVE) {
                if (peer.getState() == NodeState.SUSPECT && gossip.getInc() > peer.getInc()) {
                    peers.put(peer.getId(), peer.state(NodeState.ALIVE).inc(gossip.getInc()).touch(now));
                    return;
                }

                if (peer.getState() == NodeState.ALIVE && gossip.getInc() > peer.getInc()) {
                    peers.put(peer.getId(), peer.state(NodeState.ALIVE).inc(gossip.getInc()).touch(now));
                    return;
                }

                return;
            }

            if (gossip.getState() == NodeState.SUSPECT) {
                if (peer.getState() == NodeState.SUSPECT && gossip.getInc() > peer.getInc()) {
                    final ResolvableFuture<Void> confirm = confirmIn(confirmDelay, now, peer.getAddress());
                    peers.put(peer.getId(),
                            peer.state(NodeState.SUSPECT).confirm(confirm).inc(gossip.getInc()).touch(now));
                    return;
                }

                if (peer.getState() == NodeState.ALIVE && gossip.getInc() >= peer.getInc()) {
                    final ResolvableFuture<Void> confirm = confirmIn(confirmDelay, now, peer.getAddress());
                    peers.put(peer.getId(),
                            peer.state(NodeState.SUSPECT).confirm(confirm).inc(gossip.getInc()).touch(now));
                    return;
                }

                return;
            }

            if (gossip.getState() == NodeState.CONFIRM) {
                if (peer.getState() != NodeState.CONFIRM && gossip.getInc() > peer.getInc())
                    peers.put(peer.getId(), peer.state(NodeState.CONFIRM).inc(gossip.getInc()).touch(now));

                return;
            }
        }

        private void handlePingRequest(InetSocketAddress source, PingRequest pingRequest) throws Exception {
            final UUID id = loop.uuid();
            final long now = loop.now();

            final ResolvableFuture<Void> future = expireIn(pingRequestDelegateTimeout, id);

            final PendingPingReq pendingPingReq = new PendingPingReq(now, now + pingRequestDelegateTimeout,
                    pingRequest.getTarget(), pingRequest.getId(), source, future);

            final InetSocketAddress target = pingRequest.getTarget();

            sendPing(target, id, pendingPingReq);
        }

        /**
         * Handle a received acknowledgement.
         */
        private void handleAck(SocketAddress source, Ack ack) throws Exception {
            reporter.reportReceivedAck(ack);

            final PendingOperation any = pings.remove(ack.getPingId());

            if (any == null) {
                reporter.reportNonPendingAck(ack);
                return;
            }

            if (!any.getTimeout().resolve(null))
                return;

            /* Requests which have been performed from this node. */
            if (any instanceof PendingPing) {
                final PendingPing p = (PendingPing) any;
                Peer peer = peers.get(p.getTarget());

                if (peer == null) {
                    reporter.reportNoNodeForAck(ack.getPingId(), p.getTarget());
                    return;
                }

                peer = updateFromAck(peer, ack);

                if (peer != null)
                    peers.put(peer.getId(), peer);

                return;
            }

            /* Requests which have been performed on behalf of another node. */
            if (any instanceof PendingPingReq) {
                final PendingPingReq p = (PendingPingReq) any;
                ack(p.getSource(), p.getSourcePingId(), ack.getState(), ack.getInc());
                return;
            }
        }

        private Peer updateFromAck(Peer peer, Ack ack) {
            switch (ack.getState()) {
            case ALIVE:
                return peer.state(NodeState.ALIVE).touch(loop.now());
            case SUSPECT:
                // nothing new...
                if (peer.getState() == NodeState.SUSPECT || peer.getState() == NodeState.CONFIRM)
                    return null;

                final long now = loop.now();
                confirmIn(confirmDelay, now, peer.getAddress());
                return peer.state(NodeState.SUSPECT).touch(now);
            case CONFIRM:
                // confirm without touching to speed along peer removal.
                return peer.state(NodeState.CONFIRM);
            default:
                return null;
            }
        }

        private void handlePing(final InetSocketAddress source, final Ping ping) throws Exception {
            reporter.reportReceivedPing(ping);

            final boolean alive = Gossiper.this.alive.get();

            if (alive != previous.get()) {
                previous.set(alive);
                inc.incrementAndGet();
            }

            final NodeState state = alive ? NodeState.ALIVE : NodeState.CONFIRM;

            ack(source, ping.getId(), state, inc.get());
        }

        /* senders */

        private void ack(final InetSocketAddress target, final UUID id, final NodeState state, long inc)
                throws Exception {
            final Ack ack = new Ack(id, state, inc, buildGossip());
            send(target, ack);
            reporter.reportSentAck(ack);
        }

        private void pingRequest(final InetSocketAddress peer, final InetSocketAddress target) throws Exception {
            final UUID id = loop.uuid();
            final long now = loop.now();
            final PingRequest pingRequest = new PingRequest(id, target, buildGossip());

            final ResolvableFuture<Void> future = expireIn(pingRequestTimeout, id);
            final PendingPing pendingPing = new PendingPing(now + pingRequestTimeout, target, future);

            pings.put(id, pendingPing);
            send(peer, pingRequest);
            reporter.reportSentPingRequest(pingRequest);
        }

        private void ping(final InetSocketAddress target) throws Exception {
            final UUID id = loop.uuid();
            final long now = loop.now();

            final ResolvableFuture<Void> future = expireIn(pingTimeout, id);
            final PendingPing pendingPing = new PendingPing(now + pingTimeout, target, future);

            sendPing(target, id, pendingPing);
        }

        private void sendPing(final InetSocketAddress target, final UUID id, final PendingOperation pending)
                throws Exception {
            pings.put(id, pending);
            final Ping ping = new Ping(id, buildGossip());
            send(target, ping);
            reporter.reportSentPing(ping);
        }

        private List<Gossip> buildGossip() throws Exception {
            final List<Gossip> result = new ArrayList<>();

            result.add(myState());
            result.addAll(otherStates());

            return result;
        }

        private List<Gossip> otherStates() {
            final List<Gossip> result = new ArrayList<>();

            // do not gossip about self, it will only end badly.
            final NodeFilter filter = younger(30000);

            for (final Peer v : randomPeers(gossipLimit, filter)) {
                if (v.getId().equals(localId))
                    throw new IllegalStateException("trying to gossip about self");

                result.add(new OtherGossip(localId, v.getId(), v.getAddress(), v.getState(), v.getInc()));
            }

            return result;
        }

        private void send(InetSocketAddress target, Message data) throws Exception {
            final ByteBufferSerialWriter output = new ByteBufferSerialWriter();
            message.serialize(output, data);
            channel.send(target, output.buffer());
        }

        @Override
        public String toString() {
            return "<" + channel + ":" + alive.get() + ">";
        }
    }

    public static class Builder {
        private static final Reporter NOOP_REPORTER = new NoopReporter();

        private static final ChangeListener NOOP_LISTENER = new ChangeListener() {
            @Override
            public void peerFound(Gossiper gossiper, UUID peer) {
            }

            @Override
            public void peerLost(Gossiper gossiper, UUID peer) {
            }
        };

        private UUID id;
        private EventLoop loop;
        private List<InetSocketAddress> seeds;
        private Provider<Boolean> alive;
        private Random random;
        private Reporter reporter;
        private ChangeListener listener;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder loop(EventLoop loop) {
            if (loop == null)
                throw new IllegalArgumentException("loop must be specified");

            this.loop = loop;
            return this;
        }

        public Builder seeds(List<InetSocketAddress> seeds) {
            if (seeds == null || seeds.isEmpty())
                throw new IllegalArgumentException("seeds are required");

            this.seeds = seeds;
            return this;
        }

        public Builder alive(Provider<Boolean> alive) {
            this.alive = alive;
            return this;
        }

        public Builder random(Random random) {
            this.random = random;
            return this;
        }

        public Builder reporter(Reporter reporter) {
            this.reporter = reporter;
            return this;
        }

        public Builder listener(ChangeListener listener) {
            this.listener = listener;
            return this;
        }

        public Gossiper build() {
            if (loop == null)
                throw new IllegalStateException("loop is required");

            if (seeds == null || seeds.isEmpty())
                throw new IllegalStateException("seeds are required");

            UUID id = this.id;

            if (id == null)
                id = loop.uuid();

            Provider<Boolean> alive = this.alive;

            if (alive == null)
                alive = Providers.ofValue(true);

            Random random = this.random;

            if (random == null)
                random = new Random();

            Reporter reporter = this.reporter;

            if (reporter == null)
                reporter = NOOP_REPORTER;

            ChangeListener listener = this.listener;

            if (listener == null)
                listener = NOOP_LISTENER;

            return new Gossiper(id, loop, seeds, alive, random, reporter, listener);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
