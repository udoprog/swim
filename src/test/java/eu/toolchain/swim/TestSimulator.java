package eu.toolchain.swim;

import static eu.toolchain.swim.Providers.ofAtomic;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import eu.toolchain.swim.async.Task;
import eu.toolchain.swim.async.simulator.SimulatorEventLoop;
import eu.toolchain.swim.statistics.TallyReporter;

public class TestSimulator {
    @Test
    public void testSomething() throws Exception {
        final Random random = new Random(0);

        final SimulatorEventLoop loop = new SimulatorEventLoop(random);

        final TallyReporter reporter = new TallyReporter(loop);

        final Map<String, AtomicBoolean> alive = new HashMap<>();

        alive.put("a", new AtomicBoolean());
        alive.put("b", new AtomicBoolean());
        alive.put("c", new AtomicBoolean());

        // 5% global packet loss
        loop.setPacketLoss(0);

        // set a random delay between 10 and 200 ticks.
        loop.setRandomDelay(10, 200);

        final InetSocketAddress a = new InetSocketAddress(5000);
        final InetSocketAddress b = new InetSocketAddress(5001);
        final InetSocketAddress c = new InetSocketAddress(5002);

        final List<InetSocketAddress> seeds = new ArrayList<>();
        seeds.add(a);
        seeds.add(b);
        seeds.add(c);

        final ChangeListener listener = new ChangeListener() {
            @Override
            public void peerFound(InetSocketAddress peer) {
                System.out.println(loop.now() + ": found: " + peer);
            }

            @Override
            public void peerLost(InetSocketAddress peer) {
                System.out.println(loop.now() + ": lost: " + peer);
            }
        };

        final Provider<Boolean> defaultAlive = Providers.ofValue(true);
        final GossipService service = new GossipService(loop, seeds, defaultAlive, random, reporter,
                ChangeListener.NOOP);

        final Map<String, GossipService.Channel> channels = new HashMap<>();

        channels.put("a", loop.bind(a, service.alive(ofAtomic(alive.get("a")))));
        channels.put("b", loop.bind(b, service.listener(listener).alive(ofAtomic(alive.get("b")))));
        channels.put("c", loop.bind(c, service.alive(ofAtomic(alive.get("c")))));

        for (int i = 0; i < 10; i++) {
            channels.put(Integer.toString(i), loop.bind(new InetSocketAddress(6000 + i), service));
        }

        alive.get("a").set(true);
        alive.get("b").set(true);

        // at tick 5000, remove blocks and delays.
        loop.at(10000, new Task() {
            @Override
            public void run() throws Exception {
                alive.get("c").set(true);
                Assert.assertEquals(12, channels.get("b").members().size());
            }
        });

        loop.at(20000, new Task() {
            @Override
            public void run() throws Exception {
                // time for c to leave
                alive.get("c").set(false);
                alive.get("a").set(true);
                Assert.assertEquals(13, channels.get("a").members().size());
            }
        });

        loop.at(40000, new Task() {
            @Override
            public void run() throws Exception {
                loop.block(c);
            }
        });

        // run for 100000 ticks.
        loop.run(100000);

        List<InetSocketAddress> members = channels.get("a").members();

        Collections.sort(members, new Comparator<InetSocketAddress>() {
            @Override
            public int compare(InetSocketAddress a, InetSocketAddress b) {
                return Integer.compare(a.getPort(), b.getPort());
            }
        });

        Assert.assertEquals(12, members.size());

        for (final InetSocketAddress address : members)
            System.out.println(address);

        System.out.println("             sent pings: " + reporter.getSentPings());
        System.out.println("         received pings: " + reporter.getReceivedPings());
        System.out.println("              sent acks: " + reporter.getSentAcks());
        System.out.println("          received acks: " + reporter.getReceivedAcks());
        System.out.println("     sent ping requests: " + reporter.getSentPingRequest());
        System.out.println("                 expire: " + reporter.getExpire());
        System.out.println("        non pending ack: " + reporter.getNonPendingAck());
        System.out.println("        no node for ack: " + reporter.getNoNodeForAck());
        System.out.println("missing peer for expire: " + reporter.getMissingPeerForExpire());
    }
}
