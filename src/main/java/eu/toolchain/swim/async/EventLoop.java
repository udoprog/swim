package eu.toolchain.swim.async;

import java.net.InetSocketAddress;

public interface EventLoop {
    void bindUDP(final InetSocketAddress address, final DatagramBindListener listener)
            throws BindException;

    void bindUDP(final String string, final int i, final DatagramBindListener listener)
            throws BindException;

    void schedule(final long delay, final Task task);

    long now();
}
