package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.toolchain.swim.async.nio.NioEventLoop;

public class App {
    public static void main(final String[] args) throws Exception {

        /*
         * if this provider provides the value 'false', this node will be
         * considered dead.
         */
        final Provider<Boolean> alive = Providers.ofValue(true);

        final List<InetSocketAddress> seeds = new ArrayList<>();
        seeds.add(new InetSocketAddress("localhost", 3334));

        final NioEventLoop eventLoop = new NioEventLoop();

        final Random random = new Random(0);

        eventLoop.bind(new InetSocketAddress("localhost", 3334), new GossipService(seeds, alive,
                random));
        eventLoop.bind(new InetSocketAddress("localhost", 3333), new GossipService(seeds, alive,
                random));

        eventLoop.run();

        System.exit(0);
    }
}