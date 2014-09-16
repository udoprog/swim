package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;


public class BooleanSerializer implements Serializer<Boolean> {
    private BooleanSerializer() {
    }

    private static final Serializer<Boolean> instance = new BooleanSerializer();
    private static final byte TRUE = 0x01;
    private static final byte FALSE = 0x00;

    public static Serializer<Boolean> get() {
        return instance;
    }

    @Override
    public void serialize(ByteBuffer b, Boolean data) throws Exception {
        b.put(data ? TRUE : FALSE);
    }

    @Override
    public Boolean deserialize(ByteBuffer b) throws Exception {
        return b.get() == TRUE;
    }
}
