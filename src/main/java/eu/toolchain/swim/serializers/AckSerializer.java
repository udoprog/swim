package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.UUID;

import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.Gossip;

public class AckSerializer implements Serializer<Ack> {
    private AckSerializer() {
    }

    private static final Serializer<Ack> instance = new AckSerializer();

    public static Serializer<Ack> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, Ack data) throws Exception {
        UUIDSerializer.get().serialize(b, data.getId());
        BooleanSerializer.get().serialize(b, data.isAlive());
        SerializerUtils.serializeCollection(b, data.getGossip(), GossipSerializer.get());
    }

    @Override
    public Ack deserialize(ByteBuffer b) throws Exception {
        final UUID id = UUIDSerializer.get().deserialize(b);
        final boolean alive = BooleanSerializer.get().deserialize(b);
        final Collection<Gossip> payloads = SerializerUtils.deserializeCollection(b, GossipSerializer.get());
        return new Ack(id, alive, payloads);
    }
}