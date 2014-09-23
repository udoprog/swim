package eu.toolchain.swim.async;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface ReceivePacket {
    /**
     * Fires when receiving a packet.
     */
    void packet(InetSocketAddress source, ByteBuffer packet) throws Exception;
}
