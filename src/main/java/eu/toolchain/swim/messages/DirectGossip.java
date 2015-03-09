package eu.toolchain.swim.messages;

import java.util.UUID;

import lombok.Data;
import eu.toolchain.swim.NodeState;

@Data
public class DirectGossip implements Gossip {
    private final UUID about;
    private final NodeState state;
    private final long inc;
}