package eu.toolchain.swim.messages;

import java.util.List;
import java.util.UUID;

import lombok.Data;
import eu.toolchain.swim.NodeState;

@Data
public class Ack implements Message {
    private final UUID pingId;
    private final NodeState state;
    private final long inc;
    private final List<Gossip> gossip;
}