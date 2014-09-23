package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import lombok.Data;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;

@Data
public class GossipService implements DatagramBindListener {
    private final List<InetSocketAddress> seeds;
    private final Provider<Boolean> alive;
    private final Random random;

    @Override
    public void ready(final EventLoop eventLoop, final DatagramBindChannel channel) {
        final GossipServiceListener session = new GossipServiceListener(eventLoop, channel, seeds, alive,
                random);

        channel.register(new ReceivePacket() {
            @Override
            public void packet(final InetSocketAddress source, final ByteBuffer packet)
                    throws Exception {
                session.read(source, packet);
            }
        });

        session.start();
    }
}
