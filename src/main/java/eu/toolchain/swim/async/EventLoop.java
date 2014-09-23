package eu.toolchain.swim.async;

import java.io.IOException;
import java.net.InetSocketAddress;

import eu.toolchain.swim.GossipService;

public interface EventLoop {
    void bindUDP(final InetSocketAddress address, final DatagramBindListener listener)
            throws BindException;

    void bindUDP(final String string, final int i, final GossipService gossipService)
            throws BindException;

    void schedule(final long delay, final Task task);

    void run() throws IOException;
}
