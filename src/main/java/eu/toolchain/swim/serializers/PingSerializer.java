package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.UUID;

import eu.toolchain.swim.messages.Gossip;
import eu.toolchain.swim.messages.Ping;

public final class PingSerializer implements Serializer<Ping> {
    private PingSerializer() {
    }

    private static final Serializer<Ping> instance = new PingSerializer();

    public static Serializer<Ping> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, Ping data) throws Exception {
        UUIDSerializer.get().serialize(b, data.getId());
        SerializerUtils.serializeCollection(b, data.getPayloads(), PayloadSerializer.get());
    }

    @Override
    public Ping deserialize(ByteBuffer b) throws Exception {
        final UUID id = UUIDSerializer.get().deserialize(b);
        final Collection<Gossip> payloads = SerializerUtils.deserializeCollection(b, PayloadSerializer.get());
        return new Ping(id, payloads);
    }
}