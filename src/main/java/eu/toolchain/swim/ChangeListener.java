package eu.toolchain.swim;

import java.net.InetSocketAddress;

public interface ChangeListener<T> {
    public void peerFound(T channel, InetSocketAddress peer);

    public void peerLost(T channel, InetSocketAddress peer);
}