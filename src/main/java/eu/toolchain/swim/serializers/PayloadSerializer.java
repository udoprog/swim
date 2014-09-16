package eu.toolchain.swim.serializers;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import eu.toolchain.swim.NodeState;
import eu.toolchain.swim.messages.Gossip;

public final class PayloadSerializer implements Serializer<Gossip> {
    private static final Serializer<NodeState> peerStateSerializer = new EnumSerializer<NodeState>(NodeState.class);

    private PayloadSerializer() {
    }

    private static final Serializer<Gossip> instance = new PayloadSerializer();

    public static Serializer<Gossip> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, Gossip data) throws Exception {
        InetSocketAddressSerializer.get().serialize(b, data.getAbout());
        peerStateSerializer.serialize(b, data.getState());
        b.putLong(data.getInc());
    }

    @Override
    public Gossip deserialize(ByteBuffer b) throws Exception {
        final InetSocketAddress address = InetSocketAddressSerializer.get().deserialize(b);
        final NodeState peerState = peerStateSerializer.deserialize(b);
        final long inc = b.getLong();
        return new Gossip(address, peerState, inc);
    }
}