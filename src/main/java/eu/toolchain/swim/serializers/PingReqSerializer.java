package eu.toolchain.swim.serializers;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.UUID;

import eu.toolchain.swim.messages.Gossip;
import eu.toolchain.swim.messages.PingReq;

public final class PingReqSerializer implements Serializer<PingReq> {
    private PingReqSerializer() {
    }

    private static final Serializer<PingReq> instance = new PingReqSerializer();

    public static Serializer<PingReq> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, PingReq data) throws Exception {
        UUIDSerializer.get().serialize(b, data.getId());
        InetSocketAddressSerializer.get().serialize(b, data.getTarget());
        SerializerUtils.serializeCollection(b, data.getPayloads(), PayloadSerializer.get());
    }

    @Override
    public PingReq deserialize(ByteBuffer b) throws Exception {
        final UUID id = UUIDSerializer.get().deserialize(b);
        final InetSocketAddress target = InetSocketAddressSerializer.get().deserialize(b);
        final Collection<Gossip> payloads = SerializerUtils.deserializeCollection(b, PayloadSerializer.get());
        return new PingReq(id, target, payloads);
    }
}