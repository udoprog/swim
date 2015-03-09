package eu.toolchain.swim.messages;

import java.net.InetSocketAddress;
import java.util.UUID;

import lombok.Data;
import eu.toolchain.swim.NodeState;

@Data
public class OtherGossip implements Gossip {
    private final UUID source;
    private final UUID about;
    private final InetSocketAddress aboutAddress;
    private final NodeState state;
    private final long inc;
}