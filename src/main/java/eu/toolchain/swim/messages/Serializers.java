package eu.toolchain.swim.messages;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.toolchain.serializer.SerialReader;
import eu.toolchain.serializer.SerialWriter;
import eu.toolchain.serializer.Serializer;
import eu.toolchain.serializer.SerializerFramework;
import eu.toolchain.serializer.SerializerFramework.TypeMapping;
import eu.toolchain.serializer.TinySerializer;
import eu.toolchain.swim.NodeState;

public class Serializers {
    private final SerializerFramework serializer = TinySerializer.builder().build();

    public Serializer<Message> message() {
        final ArrayList<TypeMapping<? extends Message>> mappings = new ArrayList<>();

        mappings.add(serializer.type(0, Ack.class, ack()));
        mappings.add(serializer.type(10, Ping.class, ping()));
        mappings.add(serializer.type(20, PingReq.class, pingReq()));

        return serializer.subtypes(mappings);
    }

    public Serializer<Gossip> gossip() {
        final ArrayList<TypeMapping<? extends Gossip>> mappings = new ArrayList<>();

        mappings.add(serializer.type(10, MyStateGossip.class, new Serializer<MyStateGossip>() {
            private final Serializer<InetSocketAddress> address = address();
            private final Serializer<NodeState> state = serializer.forEnum(NodeState.values());
            private final Serializer<Long> inc = serializer.longNumber();

            @Override
            public void serialize(SerialWriter buffer, MyStateGossip value) throws IOException {
                address.serialize(buffer, value.getAbout());
                state.serialize(buffer, value.getState());
                inc.serialize(buffer, value.getInc());
            }

            @Override
            public MyStateGossip deserialize(SerialReader buffer) throws IOException {
                final InetSocketAddress about = this.address.deserialize(buffer);
                final NodeState state = this.state.deserialize(buffer);
                final long inc = this.inc.deserialize(buffer);
                return new MyStateGossip(about, state, inc);
            }
        }));

        mappings.add(serializer.type(20, OtherStateGossip.class, new Serializer<OtherStateGossip>() {
            private final Serializer<InetSocketAddress> address = address();
            private final Serializer<NodeState> state = serializer.forEnum(NodeState.values());
            private final Serializer<Long> inc = serializer.longNumber();

            @Override
            public void serialize(SerialWriter buffer, OtherStateGossip value) throws IOException {
                address.serialize(buffer, value.getSource());
                address.serialize(buffer, value.getAbout());
                state.serialize(buffer, value.getState());
                inc.serialize(buffer, value.getInc());
            }

            @Override
            public OtherStateGossip deserialize(SerialReader buffer) throws IOException {
                final InetSocketAddress source = this.address.deserialize(buffer);
                final InetSocketAddress about = this.address.deserialize(buffer);
                final NodeState state = this.state.deserialize(buffer);
                final long inc = this.inc.deserialize(buffer);
                return new OtherStateGossip(source, about, state, inc);
            }
        }));

        return serializer.subtypes(mappings);
    }

    public Serializer<InetSocketAddress> address() {
        return new Serializer<InetSocketAddress>() {
            private final Serializer<byte[]> address = serializer.byteArray();
            private final Serializer<Integer> port = serializer.integer();

            @Override
            public void serialize(SerialWriter buffer, InetSocketAddress value) throws IOException {
                byte[] a = value.getAddress().getAddress();

                if (a.length == 0)
                    throw new IllegalArgumentException("invalid address of length 0");

                address.serialize(buffer, a);
                port.serialize(buffer, value.getPort());
            }

            @Override
            public InetSocketAddress deserialize(SerialReader buffer) throws IOException {
                final byte[] address = this.address.deserialize(buffer);
                final int port = this.port.deserialize(buffer);

                final InetAddress addr;

                if (address.length == 4) {
                    addr = Inet4Address.getByAddress(address);
                } else if (address.length == 16) {
                    addr = Inet6Address.getByAddress(address);
                } else {
                    throw new IllegalArgumentException("invalid address length: " + address.length);
                }

                return new InetSocketAddress(addr, port);
            }
        };
    }

    public Serializer<Ack> ack() {
        return new Serializer<Ack>() {
            private final Serializer<UUID> id = serializer.uuid();
            private final Serializer<Boolean> ok = serializer.bool();
            private final Serializer<List<Gossip>> gossip = serializer.list(gossip());

            @Override
            public void serialize(SerialWriter buffer, Ack value) throws IOException {
                id.serialize(buffer, value.getId());
                ok.serialize(buffer, value.isOk());
                gossip.serialize(buffer, value.getGossip());
            }

            @Override
            public Ack deserialize(SerialReader buffer) throws IOException {
                final UUID id = this.id.deserialize(buffer);
                final boolean ok = this.ok.deserialize(buffer);
                final List<Gossip> gossip = this.gossip.deserialize(buffer);
                return new Ack(id, ok, gossip);
            }
        };
    }

    public Serializer<Ping> ping() {
        return new Serializer<Ping>() {
            private final Serializer<UUID> id = serializer.uuid();
            private final Serializer<List<Gossip>> gossip = serializer.list(gossip());

            @Override
            public void serialize(SerialWriter buffer, Ping value) throws IOException {
                id.serialize(buffer, value.getId());
                gossip.serialize(buffer, value.getGossip());
            }

            @Override
            public Ping deserialize(SerialReader buffer) throws IOException {
                final UUID id = this.id.deserialize(buffer);
                final List<Gossip> gossip = this.gossip.deserialize(buffer);
                return new Ping(id, gossip);
            }
        };
    }

    public Serializer<PingReq> pingReq() {
        return new Serializer<PingReq>() {
            private final Serializer<UUID> id = serializer.uuid();
            private final Serializer<InetSocketAddress> target = address();
            private final Serializer<List<Gossip>> gossip = serializer.list(gossip());

            @Override
            public void serialize(SerialWriter buffer, PingReq value) throws IOException {
                id.serialize(buffer, value.getId());
                target.serialize(buffer, value.getTarget());
                gossip.serialize(buffer, value.getGossip());
            }

            @Override
            public PingReq deserialize(SerialReader buffer) throws IOException {
                final UUID id = this.id.deserialize(buffer);
                final InetSocketAddress target = this.target.deserialize(buffer);
                final List<Gossip> gossip = this.gossip.deserialize(buffer);
                return new PingReq(id, target, gossip);
            }
        };
    }
}