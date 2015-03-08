package eu.toolchain.swim;

import static eu.toolchain.swim.NodeFilters.address;
import static eu.toolchain.swim.NodeFilters.not;
import static eu.toolchain.swim.NodeFilters.younger;

import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.serializer.Serializer;
import eu.toolchain.serializer.io.ByteBufferSerialReader;
import eu.toolchain.serializer.io.ByteBufferSerialWriter;
import eu.toolchain.swim.GossipService.Channel;
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
import eu.toolchain.swim.statistics.Reporter;

@Slf4j
@RequiredArgsConstructor
public class GossipService implements DatagramBindListener<Channel> {
    private static final long DEFAULT_EXPIRE_TIMER = 1000;
    private static final long DEFAULT_SEED_TIMER = 10000;
    private static final long PEER_TIMEOUT = 30000;
    private static final long DEFAULT_PING_TIMEOUT = 2000;
    private static final long DEFAULT_PING_REQ_TIMEOUT = 1500;
    private static final long DEFAULT_PING_REQ_DELEGATE_TIMEOUT = 1000;
    private static final long DEFAULT_GOSSIP_LIMIT = 20;

    private final EventLoop loop;
    private final List<InetSocketAddress> seeds;
    private final Provider<Boolean> alive;
    private final Random random;
    private final Reporter reporter;
    private final ChangeListener<Channel> listener;

    private final Serializers s = new Serializers();
    private final Serializer<Message> message = s.message();

    private final long expireTimer = DEFAULT_EXPIRE_TIMER;
    private final long seedTimer = DEFAULT_SEED_TIMER;
    private final long peerTimeout = PEER_TIMEOUT;
    private final long pingTimeout = DEFAULT_PING_TIMEOUT;
    private final long pingRequestTimeout = DEFAULT_PING_REQ_TIMEOUT;
    private final long pingRequestDelegateTimeout = DEFAULT_PING_REQ_DELEGATE_TIMEOUT;
    private final long gossipLimit = DEFAULT_GOSSIP_LIMIT;

    public GossipService alive(Provider<Boolean> alive) {
        return new GossipService(loop, seeds, alive, random, reporter, listener);
    }

    public GossipService listener(ChangeListener<Channel> listener) {
        return new GossipService(loop, seeds, alive, random, reporter, listener);
    }

    @Override
    public Channel ready(final DatagramBindChannel channel) {
        final Channel session = new Channel(channel);

        channel.register(new ReceivePacket() {
            @Override
            public void packet(final InetSocketAddress source, final ByteBuffer packet) throws Exception {
                session.read(source, packet);
            }
        });

        session.start();
        return session;
    }

    @RequiredArgsConstructor
    public class Channel implements DatagramBindChannel {
        private final DatagramBindChannel channel;

        private final Map<InetSocketAddress, Peer> peers = new ConcurrentHashMap<>();
        private final AtomicReference<Peer> local = new AtomicReference<>();
        private final AtomicReference<Set<InetSocketAddress>> alivePeers = new AtomicReference<>();

        /**
         * Maintained lists of random pools to make sure entries are fetched uniformly randomly.
         */
        private final Map<NodeFilter, Queue<InetSocketAddress>> randomPools = new HashMap<>();

        /* pending pings */
        private Map<UUID, PendingOperation> pings = new HashMap<>();

        @Override
        public InetSocketAddress getBindAddress() {
            return channel.getBindAddress();
        }

        @Override
        public void send(InetSocketAddress target, ByteBuffer output) throws IOException {
            channel.send(target, output);
        }

        @Override
        public void register(ReceivePacket listener) {
            channel.register(listener);
        }

        void read(InetSocketAddress source, ByteBuffer input) throws Exception {
            final Collection<Gossip> gossip = receive(source, input);
            handleGossip(source, gossip);
        }

        public List<InetSocketAddress> members() {
            final List<InetSocketAddress> members = new ArrayList<>();

            if (alive.get())
                members.add(channel.getBindAddress());

            for (final Peer data : peers.values()) {
                if (data.getAddress().equals(channel.getBindAddress()))
                    continue;

                if (data.isAlive())
                    members.add(data.getAddress());
            }

            return members;
        }

        private void start() {
            // local node
            local.set(new Peer(channel.getBindAddress(), NodeState.ALIVE, 1, loop.now()));

            loop.schedule(0, new Task() {
                @Override
                public void run() throws Exception {
                    try {
                        sendRandomPings();
                    } catch (Exception e) {
                        log.error("Peer pingin failed", e);
                    }

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
                if (!peers.containsKey(seed) && !channel.getBindAddress().equals(seed))
                    ping(seed);
            }
        }

        private void sendRandomPings() throws Exception {
            final Collection<Peer> peers = randomPeers(10, not(address(channel.getBindAddress())));

            for (Peer node : peers)
                ping(node.getAddress());
        }

        private void expireIn(long delay, final UUID id) {
            loop.schedule(delay, new Task() {
                @Override
                public void run() throws Exception {
                    final PendingOperation expire = pings.remove(id);

                    if (expire == null)
                        return;

                    reporter.reportExpire(id, expire);

                    if (expire instanceof PendingPing) {
                        expirePendingPing(id, (PendingPing) expire);
                        return;
                    }

                    if (expire instanceof PendingPingReq) {
                        expirePendingPingReq(id, (PendingPingReq) expire);
                        return;
                    }
                }
            });
        }

        private void expirePeers() throws Exception {
            final long now = loop.now();

            for (final Peer p : new ArrayList<>(peers.values())) {
                // is timeout in the future?
                if (p.getUpdated() + peerTimeout > now)
                    continue;

                log.warn("{}: Removing {} since not seen for {} seconds", channel.getBindAddress(), p,
                        TimeUnit.SECONDS.convert(peerTimeout, TimeUnit.MILLISECONDS));
                peers.remove(p.getAddress());
            }
        }

        private void expirePendingPingReq(UUID id, PendingPingReq expired) throws Exception {
            final Peer peer = this.peers.get(expired.getTarget());

            if (peer == null) {
                reporter.reportMissingPeerForExpire(id, expired);
                return;
            }

            peers.put(expired.getTarget(), peer.state(NodeState.SUSPECT).touch(loop.now()));
            ack(expired.getSource(), expired.getSourcePingId(), NodeState.SUSPECT);
        }

        private void expirePendingPing(UUID id, PendingPing expired) throws Exception {
            final Peer data = this.peers.get(expired.getTarget());

            if (data == null) {
                reporter.reportMissingPeerForExpire(id, expired);
                return;
            }

            // select a random peer, that is _not_ the just recently pinged address.
            final Collection<Peer> nodes = randomPeers(1, not(address(expired.getTarget())));

            // we are not connected with anyone :(
            if (nodes.isEmpty())
                return;

            final Peer node = nodes.iterator().next();

            pingRequest(node.getAddress(), expired.getTarget());
        }

        private Collection<Gossip> receive(InetSocketAddress source, final ByteBuffer input) throws Exception {
            final ByteBufferSerialReader reader = new ByteBufferSerialReader(input);

            final Message message = GossipService.this.message.deserialize(reader);

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
                peers.put(source, n.touch(loop.now()));

            return message.getGossip();
        }

        /* handlers */

        private void handleGossip(InetSocketAddress source, Collection<Gossip> payloads) throws Exception {
            for (final Gossip g : payloads) {
                if (g instanceof OtherGossip) {
                    handleOtherGossip(source, (OtherGossip) g);
                    continue;
                }

                if (g instanceof DirectGossip) {
                    handleDirectGossip(source, (DirectGossip) g);
                    continue;
                }
            }
        }

        private void checkAlive() {
            final Set<InetSocketAddress> members = new HashSet<>(members());
            final Set<InetSocketAddress> alive = alivePeers.get();

            final Set<InetSocketAddress> added;

            if (alive != null) {
                added = new HashSet<>(members);
                added.removeAll(alive);
            } else {
                added = members;
            }

            final Set<InetSocketAddress> removed;

            if (alive != null) {
                removed = new HashSet<>(alive);
                removed.removeAll(members);
            } else {
                removed = new HashSet<>();
            }

            if (alive == null || !added.isEmpty() || !removed.isEmpty())
                alivePeers.set(members);

            for (final InetSocketAddress add : added)
                listener.peerFound(this, add);

            for (final InetSocketAddress remove : removed)
                listener.peerLost(this, remove);
        }

        private void handleOtherGossip(InetSocketAddress source, OtherGossip g) {
            if (g.getAbout().equals(channel.getBindAddress()))
                return;

            final long now = loop.now();

            Peer about = peers.get(g.getAbout());

            if (about == null)
                about = new Peer(g.getAbout(), NodeState.SUSPECT, 0, now);

            // TODO: there used to be a block here about handling gossip about setting the state of an already existing
            // peer, should we have it?
            // Something like:
            //
            // if (g.getInc() > bout.getInc())
            // about = about.state(g.getState()).touch(now);

            about = about.gossip(new Rumor(now, g.getSource(), g.getInc(), g.getState()));

            peers.put(about.getAddress(), about);
        }

        /**
         * Receive first-hand account directly from a node, this overrides everything.
         *
         * @param source
         */
        private void handleDirectGossip(InetSocketAddress source, DirectGossip g) {
            if (g.getAbout().equals(channel.getBindAddress()))
                throw new IllegalStateException("gossiping about self");

            final long now = loop.now();

            Peer about = peers.get(g.getAbout());

            if (about == null) {
                about = new Peer(g.getAbout(), g.getState(), g.getInc(), now);
            } else {
                about = about.touch(now);
            }

            if (about.getInc() > g.getInc()) {
                reporter.reportDirectGossipIncError(g);
                return;
            }

            about = about.state(g.getState()).inc(g.getInc());

            // something fresher than we know.
            // since this is an incremental update, we know that it has to originate
            // from the node in question, or someone who has a more recent (direct) picture with that node.
            peers.put(about.getAddress(), about);
        }

        private void handlePingRequest(InetSocketAddress source, PingRequest pingRequest) throws Exception {
            final UUID id = loop.uuid();
            final long now = loop.now();

            expireIn(pingRequestDelegateTimeout, id);

            pings.put(id, new PendingPingReq(now, now + pingRequestDelegateTimeout, pingRequest.getTarget(),
                    pingRequest.getId(), source));

            final Ping ping = new Ping(id, buildGossip());
            send(pingRequest.getTarget(), ping);
            reporter.reportSentPing(ping);
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

            /* Requests which have been performed from this node. */
            if (any instanceof PendingPing) {
                final PendingPing p = (PendingPing) any;
                final Peer peer = peers.get(p.getTarget());

                if (peer == null) {
                    reporter.reportNoNodeForAck(ack.getPingId(), p.getTarget());
                    return;
                }

                peers.put(p.getTarget(), peer.state(ack.getState()).touch(loop.now()));
                return;
            }

            /* Requests which have been performed on behalf of another node. */
            if (any instanceof PendingPingReq) {
                final PendingPingReq p = (PendingPingReq) any;
                // send back acknowledgement to the source peer.
                ack(p.getSource(), p.getSourcePingId(), ack.getState());
                return;
            }
        }

        private void handlePing(final InetSocketAddress source, final Ping ping) throws Exception {
            reporter.reportReceivedPing(ping);
            ack(source, ping.getId(), alive.get() ? NodeState.ALIVE : NodeState.DEAD);
        }

        /* senders */

        private void ack(final InetSocketAddress target, final UUID id, final NodeState state) throws Exception {
            final Ack ack = new Ack(id, state, buildGossip());
            send(target, ack);
            reporter.reportSentAck(ack);
        }

        private void pingRequest(final InetSocketAddress peer, final InetSocketAddress target) throws Exception {
            final UUID id = loop.uuid();
            final long now = loop.now();
            final PingRequest pingRequest = new PingRequest(id, target, buildGossip());

            pings.put(id, new PendingPing(now + pingRequestTimeout, target));
            expireIn(pingRequestTimeout, id);
            send(peer, pingRequest);
            reporter.reportSentPingRequest(pingRequest);
        }

        private void ping(final InetSocketAddress target) throws Exception {
            final UUID id = loop.uuid();
            final long now = loop.now();
            final Ping ping = new Ping(id, buildGossip());

            pings.put(id, new PendingPing(now + pingTimeout, target));
            expireIn(pingTimeout, id);
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

            final InetSocketAddress address = channel.getBindAddress();

            // do not gossip about self, it will only end badly.
            final NodeFilter filter = younger(30000);

            for (final Peer v : randomPeers(gossipLimit, filter)) {
                if (v.getAddress().equals(channel.getBindAddress()))
                    throw new IllegalStateException("trying to gossip about self");

                result.add(new OtherGossip(address, v.getAddress(), v.getState(), v.getInc()));
            }

            return result;
        }

        private DirectGossip myState() {
            Peer me = local.get();

            if (me == null)
                throw new IllegalStateException("information about self should never dissapear");

            Peer external = peers.get(me.getAddress());

            final NodeState actual = alive.get() ? NodeState.ALIVE : NodeState.DEAD;

            // has our state changed
            if (me.getState() != actual || isBadRumorSpreading(me, external, actual)) {
                me = me.touch(loop.now()).state(actual).inc(me.getInc() + 1);
                local.set(me);
            }

            return new DirectGossip(me.getAddress(), me.getState(), me.getInc());
        }

        private boolean isBadRumorSpreading(Peer node, Peer external, NodeState actual) {
            // no opinion
            if (external == null)
                return false;

            // external opinion based on outdated information.
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
}
