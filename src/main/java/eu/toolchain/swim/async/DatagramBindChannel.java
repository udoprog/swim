package eu.toolchain.swim.async;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface DatagramBindChannel {
    InetSocketAddress getBindAddress();

    void send(InetSocketAddress target, ByteBuffer output);

    void register(ReceivePacket listener);
}
