package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;
import eu.toolchain.swim.async.Task;

@Data
@Slf4j
public class GossipService implements DatagramBindListener {
    private final List<Node> seeds;
    private final Provider<Boolean> alive;
    private final Random random;

    @Override
    public void ready(EventLoop eventLoop, DatagramBindChannel channel) {
        final Task expire = new Task() {
            @Override
            public void run() {
                log.info("EXPIRE!");
            }
        };

        final Task expire2 = new Task() {
            @Override
            public void run() {
                log.info("EXPIRE 2");
            }
        };

        eventLoop.schedule(2000, expire);
        eventLoop.schedule(4000, expire2);

        final GossipServiceImpl session = new GossipServiceImpl(channel, seeds, alive,
                random);

        channel.register(new ReceivePacket() {
            @Override
            public void packet(InetSocketAddress source, ByteBuffer packet)
                    throws Exception {
                session.read(source, packet);
            }
        });
    }
}
