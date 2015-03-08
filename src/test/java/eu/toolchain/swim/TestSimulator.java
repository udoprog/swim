package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import eu.toolchain.swim.async.Task;
import eu.toolchain.swim.async.simulator.PacketFilter;
import eu.toolchain.swim.async.simulator.SimulatorEventLoop;

public class TestSimulator {
    @Test
    public void testSomething() throws Exception {
        final AtomicBoolean bState = new AtomicBoolean(true);

        /* if this provider provides the value 'false', this node will be considered dead. */
        final Provider<Boolean> bAlive = new Provider<Boolean>() {
            @Override
            public Boolean get() {
                return bState.get();
            }
        };

        final Provider<Boolean> alive = Providers.ofValue(true);
        final Random random = new Random(0);

        final SimulatorEventLoop loop = new SimulatorEventLoop(random);

        // 5% global packet loss
        // loop.setPacketLoss(20);

        // set a random delay between 10 and 200 ticks.
        loop.setRandomDelay(10, 200);

        final InetSocketAddress a = new InetSocketAddress(5555);
        final InetSocketAddress b = new InetSocketAddress(5556);
        final InetSocketAddress c = new InetSocketAddress(5557);

        final List<InetSocketAddress> seeds = new ArrayList<>();
        seeds.add(a);
        seeds.add(b);
        seeds.add(c);

        // no traffic from a to b will pass.
        final PacketFilter b1 = loop.block(b, a);
        final PacketFilter b2 = loop.block(c, a);

        // traffic from b to c is delayed by 500 ticks.
        final PacketFilter d1 = loop.delay(b, c, 500);
        // traffic from c to b is delayed by 700 ticks.
        final PacketFilter d2 = loop.delay(c, b, 700);

        final GossipService aService = new GossipService(new ArrayList<InetSocketAddress>(), alive, random);

        loop.bind(a, aService);
        loop.bind(b, new GossipService(seeds, bAlive, random));
        loop.bind(c, new GossipService(seeds, alive, random));

        for (int i = 0; i < 10; i++) {
            final InetSocketAddress addr = new InetSocketAddress(6000 + i);
            loop.bind(addr, new GossipService(seeds, alive, random));
        }

        // at tick 4000, remove blocks and delays.
        loop.at(5000, new Task() {
            @Override
            public void run() throws Exception {
                loop.cancel(b1, b2, d1, d2);
                bState.set(false);
            }
        });

        // run for 10000 ticks.
        loop.run(100000);

        List<InetSocketAddress> members = aService.members();
        Collections.sort(members, new Comparator<InetSocketAddress>() {
            @Override
            public int compare(InetSocketAddress a, InetSocketAddress b) {
                return Integer.compare(a.getPort(), b.getPort());
            }
        });

        for (final InetSocketAddress address : members)
            System.out.println(address);
    }
}
