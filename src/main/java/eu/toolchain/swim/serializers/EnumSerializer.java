package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;

public class EnumSerializer<T extends Enum<T>> implements Serializer<T> {
    private final Enum<T>[] constants;

    public EnumSerializer(Class<? extends Enum<T>> type) {
        this.constants = type.getEnumConstants();
    }

    @Override
    public void serialize(ByteBuffer b, T data) throws Exception {
        b.putInt(data.ordinal());
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(ByteBuffer b) throws Exception {
        return (T)constants[b.getInt()];
    }
}
