package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.GossipService.Channel;
import eu.toolchain.swim.async.nio.NioEventLoop;
import eu.toolchain.swim.statistics.TallyReporter;

@Slf4j
public class App {
    public static void main(final String[] args) throws Exception {
        /* if this provider provides the value 'false', this node will be considered dead. */
        final Provider<Boolean> alive = Providers.ofValue(true);

        final List<InetSocketAddress> seeds = new ArrayList<>();
        seeds.add(new InetSocketAddress(3000));

        final NioEventLoop loop = new NioEventLoop();

        final Random random = new Random(0);

        final TallyReporter reporter = new TallyReporter(loop);

        final ChangeListener<GossipService.Channel> listener = new ChangeListener<GossipService.Channel>() {
            @Override
            public void peerLost(Channel channel, InetSocketAddress peer) {
                log.info("lost: {}", peer);
            }

            @Override
            public void peerFound(Channel channel, InetSocketAddress peer) {
                log.info("found: {}", peer);
            }
        };

        final int base = 3000;

        loop.bind(new InetSocketAddress(base), new GossipService(loop, seeds, alive, random, reporter, listener));
        loop.bind(new InetSocketAddress(base + 1), new GossipService(loop, seeds, alive, random, reporter, listener));
        loop.bind(new InetSocketAddress(base + 2), new GossipService(loop, seeds, alive, random, reporter, listener));

        loop.run();

        System.exit(0);
    }
}