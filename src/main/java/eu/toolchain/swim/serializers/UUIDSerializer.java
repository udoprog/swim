package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UUIDSerializer implements Serializer<UUID> {
    private UUIDSerializer() {
    }

    private static final Serializer<UUID> instance = new UUIDSerializer();

    public static Serializer<UUID> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, UUID data) throws Exception {
        b.putLong(data.getMostSignificantBits());
        b.putLong(data.getLeastSignificantBits());
    }

    @Override
    public UUID deserialize(ByteBuffer b) throws Exception {
        final long most = b.getLong();
        final long least = b.getLong();
        return new UUID(most, least);
    }
}
