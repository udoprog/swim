package eu.toolchain.swim.messages;

import java.util.UUID;

import eu.toolchain.swim.NodeState;

public interface Gossip {
    public UUID getAbout();

    public NodeState getState();

    public long getInc();
}