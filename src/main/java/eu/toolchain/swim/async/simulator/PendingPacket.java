package eu.toolchain.swim.async.simulator;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import lombok.Data;

@Data
public class PendingPacket {
    private final long tick;
    private final InetSocketAddress source;
    private final InetSocketAddress destination;
    private final ByteBuffer packet;

    public PendingPacket addTicks(long ticks) {
        return new PendingPacket(tick + ticks, source, destination, packet);
    }
}
