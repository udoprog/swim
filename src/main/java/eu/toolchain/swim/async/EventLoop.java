package eu.toolchain.swim.async;

import java.net.InetSocketAddress;

public interface EventLoop {
    void bind(final InetSocketAddress address, final DatagramBindListener listener)
            throws BindException;

    void bind(final String string, final int i, final DatagramBindListener listener)
            throws BindException;

    void schedule(final long delay, final Task task);

    long now();
}
