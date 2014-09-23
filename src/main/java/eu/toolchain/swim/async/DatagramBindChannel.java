package eu.toolchain.swim.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface DatagramBindChannel {
    InetSocketAddress getBindAddress();

    void send(final InetSocketAddress target, final ByteBuffer output) throws IOException;

    void register(final ReceivePacket listener);
}
