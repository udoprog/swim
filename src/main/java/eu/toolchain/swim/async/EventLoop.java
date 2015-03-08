package eu.toolchain.swim.async;

import java.net.InetSocketAddress;
import java.util.UUID;

public interface EventLoop {
    void bind(final InetSocketAddress address, final DatagramBindListener listener) throws BindException;

    void bind(final String string, final int i, final DatagramBindListener listener) throws BindException;

    void schedule(final long delay, final Task task);

    long now();

    UUID uuid();
}
