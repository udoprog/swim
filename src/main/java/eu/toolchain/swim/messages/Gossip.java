package eu.toolchain.swim.messages;

import java.net.InetSocketAddress;

import lombok.Data;
import eu.toolchain.swim.NodeState;

@Data
public class Gossip {
    final InetSocketAddress about;
    final NodeState state;
    final long inc;
}
