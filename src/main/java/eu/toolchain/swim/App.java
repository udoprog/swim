package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) throws Exception {
        final List<Node> seeds = new ArrayList<>();
        seeds.add(new Node(new InetSocketAddress("localhost", 3334)));

        /* if this provider provides the value 'false', this node will be considered dead. */
        final Provider<Boolean> alive = Providers.ofValue(true);
        final InetSocketAddress address = new InetSocketAddress("localhost", 3333);

        final GossipService server = new GossipService(address, seeds, alive);
        server.run();

        System.exit(0);
    }
}
