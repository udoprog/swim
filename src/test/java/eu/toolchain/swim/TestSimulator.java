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

        final SimulatorEventLoop loop = new SimulatorEventLoop();

        final Random random = new Random(0);

        final InetSocketAddress a = new InetSocketAddress(5555);
        final InetSocketAddress b = new InetSocketAddress(5556);
        final InetSocketAddress c = new InetSocketAddress(5557);

        final List<InetSocketAddress> seeds = new ArrayList<>();
        seeds.add(a);
        seeds.add(b);
        seeds.add(c);

        final PacketFilter block = loop.block(a, b);

        final PacketFilter d1 = loop.delay(b, c, 500);
        final PacketFilter d2 = loop.delay(c, b, 700);

        loop.bind(a, new GossipService(seeds, alive,
                random));
        loop.bind(b, new GossipService(seeds, alive,
                random));
        loop.bind(c, new GossipService(seeds, alive,
                random));

        loop.at(4000, new Task() {
            @Override
            public void run() throws Exception {
                loop.cancel(block, d1, d2);
            }
        });

        loop.run(10000);
    }
}
