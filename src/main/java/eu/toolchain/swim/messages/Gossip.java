package eu.toolchain.swim.messages;

import java.net.InetSocketAddress;

import eu.toolchain.swim.NodeState;

public interface Gossip {
    public InetSocketAddress getAbout();

    public NodeState getState();

    public long getInc();
}