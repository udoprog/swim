package eu.toolchain.swim.messages;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import eu.toolchain.serializer.Serializer;
import eu.toolchain.serializer.io.ByteBufferSerialReader;
import eu.toolchain.serializer.io.ByteBufferSerialWriter;
import eu.toolchain.swim.NodeState;

public class TestSerializers {
    private final Serializers s = new Serializers();

    private final InetSocketAddress addr = new InetSocketAddress("localhost", 1234);

    private <T> void roundtrip(T instance, Serializer<T> serializer) throws IOException {
        final ByteBufferSerialWriter writer = new ByteBufferSerialWriter();
        serializer.serialize(writer, instance);
        final ByteBufferSerialReader reader = new ByteBufferSerialReader(writer.buffer());
        final T result = serializer.deserialize(reader);
        Assert.assertEquals(instance, result);
    }

    @Test
    public void testMessages() throws IOException {
        roundtrip(new DirectGossip(UUID.randomUUID(), NodeState.ALIVE, 40), s.gossip());
        roundtrip(new OtherGossip(UUID.randomUUID(), UUID.randomUUID(), addr, NodeState.ALIVE, 40), s.gossip());
    }

    @Test
    public void testGossip() throws IOException {
        ArrayList<Gossip> gossip = new ArrayList<Gossip>();

        gossip.add(new DirectGossip(UUID.randomUUID(), NodeState.ALIVE, 40));
        gossip.add(new OtherGossip(UUID.randomUUID(), UUID.randomUUID(), addr, NodeState.ALIVE, 40));

        roundtrip(new Ping(UUID.randomUUID(), gossip), s.ping());
        roundtrip(new Ack(UUID.randomUUID(), NodeState.SUSPECT, 0l, gossip), s.ack());
    }
}