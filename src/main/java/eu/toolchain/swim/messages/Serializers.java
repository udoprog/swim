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
        mappings.add(serializer.type(20, PingRequest.class, pingReq()));

        return serializer.subtypes(mappings);
    }

    public Serializer<Gossip> gossip() {
        final ArrayList<TypeMapping<? extends Gossip>> mappings = new ArrayList<>();

        mappings.add(serializer.type(10, DirectGossip.class, new Serializer<DirectGossip>() {
            private final Serializer<InetSocketAddress> address = address();
            private final Serializer<NodeState> state = serializer.forEnum(NodeState.values());
            private final Serializer<Long> inc = serializer.longNumber();

            @Override
            public void serialize(SerialWriter buffer, DirectGossip value) throws IOException {
                address.serialize(buffer, value.getAbout());
                state.serialize(buffer, value.getState());
                inc.serialize(buffer, value.getInc());
            }

            @Override
            public DirectGossip deserialize(SerialReader buffer) throws IOException {
                final InetSocketAddress about = this.address.deserialize(buffer);
                final NodeState state = this.state.deserialize(buffer);
                final long inc = this.inc.deserialize(buffer);
                return new DirectGossip(about, state, inc);
            }
        }));

        mappings.add(serializer.type(20, OtherGossip.class, new Serializer<OtherGossip>() {
            private final Serializer<InetSocketAddress> address = address();
            private final Serializer<NodeState> state = serializer.forEnum(NodeState.values());
            private final Serializer<Long> inc = serializer.longNumber();

            @Override
            public void serialize(SerialWriter buffer, OtherGossip value) throws IOException {
                address.serialize(buffer, value.getSource());
                address.serialize(buffer, value.getAbout());
                state.serialize(buffer, value.getState());
                inc.serialize(buffer, value.getInc());
            }

            @Override
            public OtherGossip deserialize(SerialReader buffer) throws IOException {
                final InetSocketAddress source = this.address.deserialize(buffer);
                final InetSocketAddress about = this.address.deserialize(buffer);
                final NodeState state = this.state.deserialize(buffer);
                final long inc = this.inc.deserialize(buffer);
                return new OtherGossip(source, about, state, inc);
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
            private final Serializer<Boolean> alive = serializer.bool();
            private final Serializer<List<Gossip>> gossip = serializer.list(gossip());

            @Override
            public void serialize(SerialWriter buffer, Ack value) throws IOException {
                id.serialize(buffer, value.getPingId());
                alive.serialize(buffer, value.isAlive());
                gossip.serialize(buffer, value.getGossip());
            }

            @Override
            public Ack deserialize(SerialReader buffer) throws IOException {
                final UUID id = this.id.deserialize(buffer);
                final boolean alive = this.alive.deserialize(buffer);
                final List<Gossip> gossip = this.gossip.deserialize(buffer);
                return new Ack(id, alive, gossip);
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

    public Serializer<PingRequest> pingReq() {
        return new Serializer<PingRequest>() {
            private final Serializer<UUID> id = serializer.uuid();
            private final Serializer<InetSocketAddress> target = address();
            private final Serializer<List<Gossip>> gossip = serializer.list(gossip());

            @Override
            public void serialize(SerialWriter buffer, PingRequest value) throws IOException {
                id.serialize(buffer, value.getId());
                target.serialize(buffer, value.getTarget());
                gossip.serialize(buffer, value.getGossip());
            }

            @Override
            public PingRequest deserialize(SerialReader buffer) throws IOException {
                final UUID id = this.id.deserialize(buffer);
                final InetSocketAddress target = this.target.deserialize(buffer);
                final List<Gossip> gossip = this.gossip.deserialize(buffer);
                return new PingRequest(id, target, gossip);
            }
        };
    }
}