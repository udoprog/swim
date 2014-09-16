package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;

public interface Serializer<T> {
    public void serialize(ByteBuffer b, T data) throws Exception;
    public T deserialize(ByteBuffer b) throws Exception;
}
