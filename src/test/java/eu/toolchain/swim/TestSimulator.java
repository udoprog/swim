package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import eu.toolchain.swim.async.Task;
import eu.toolchain.swim.async.simulator.PacketFilter;
import eu.toolchain.swim.async.simulator.SimulatorEventLoop;

public class TestSimulator {
    @Test
    public void testSomething() throws Exception {
        /*
         * if this provider provides the value 'false', this node will be
         * considered dead.
         */
        final Provider<Boolean> alive = Providers.ofValue(true);
        final Random random = new Random(0);

        final SimulatorEventLoop loop = new SimulatorEventLoop(random);

        // 5% global packet loss
        loop.setPacketLoss(5);

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
        final PacketFilter block = loop.block(a, b);

        // traffic from b to c is delayed by 500 ticks.
        final PacketFilter d1 = loop.delay(b, c, 500);
        // traffic from c to b is delayed by 700 ticks.
        final PacketFilter d2 = loop.delay(c, b, 700);

        loop.bind(a, new GossipService(seeds, alive, random));
        loop.bind(b, new GossipService(seeds, alive, random));
        loop.bind(c, new GossipService(seeds, alive, random));

        // at tick 4000, remove blocks and delays.
        loop.at(4000, new Task() {
            @Override
            public void run() throws Exception {
                loop.cancel(block, d1, d2);
            }
        });

        // run for 10000 ticks.
        loop.run(10000);
    }
}
