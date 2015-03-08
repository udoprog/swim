package eu.toolchain.swim.messages;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class PingRequest implements Message {
    private final UUID id;
    private final InetSocketAddress target;
    private final List<Gossip> gossip;
}
