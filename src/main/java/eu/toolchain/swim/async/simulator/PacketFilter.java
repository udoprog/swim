package eu.toolchain.swim.async.simulator;

public interface PacketFilter {
    public PendingPacket filter(PendingPacket p);
}
