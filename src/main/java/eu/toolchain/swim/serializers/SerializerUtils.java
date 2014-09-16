package eu.toolchain.swim.serializers;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

public class SerializerUtils {
    public static <T> void serializeCollection(ByteBuffer b, Collection<T> collection, Serializer<T> serializer) throws Exception {
        if (collection.size() > 0xff)
            throw new IllegalArgumentException("Maximum allowed size 255 of collection exceeded: " + collection.size());

        b.put((byte) collection.size());

        for (final T p : collection) {
            serializer.serialize(b, p);
        }
    }

    public static <T> Collection<T> deserializeCollection(ByteBuffer b, Serializer<T> serializer) throws Exception {
        final int size = 0xff & b.get();

        final Collection<T> collection = new ArrayList<>(size);

        int i = 0;

        while (i++ < size) {
            collection.add(serializer.deserialize(b));
        }

        return collection;
    }
}
