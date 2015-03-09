package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.async.nio.NioEventLoop;

@Slf4j
public class App {
    public static void main(final String[] args) throws Exception {
        /* if this provider provides the value 'false', this node will be considered dead. */
        final Provider<Boolean> alive = Providers.ofValue(true);

        final List<InetSocketAddress> seeds = new ArrayList<>();
        seeds.add(new InetSocketAddress(3000));
        seeds.add(new InetSocketAddress(4000));

        final NioEventLoop loop = new NioEventLoop();

        final Random random = new Random(0);

        final ChangeListener listener = new ChangeListener() {
            @Override
            public void peerLost(Gossiper channel, UUID peer) {
                log.info("lost: {}", peer);
            }

            @Override
            public void peerFound(Gossiper channel, UUID peer) {
                log.info("found: {}", peer);
            }
        };

        final int base = 4000;

        final Gossiper.Builder gossiper = Gossiper.builder().listener(listener).loop(loop).seeds(seeds).random(random);

        loop.bind(new InetSocketAddress(base), gossiper.build());
        loop.bind(new InetSocketAddress(base + 1), gossiper.build());
        loop.bind(new InetSocketAddress(base + 2), gossiper.build());

        loop.run();

        System.exit(0);
    }
}