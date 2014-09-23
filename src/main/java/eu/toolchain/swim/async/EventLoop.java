package eu.toolchain.swim.async;

import java.net.InetSocketAddress;

import eu.toolchain.swim.GossipService;

public interface EventLoop {
    void bindUDP(InetSocketAddress address, DatagramBindListener listener)
            throws BindException;

    void bindUDP(String string, int i, GossipService gossipService)
            throws BindException;

    void schedule(long delay, Task task);

    void run();
}
