package eu.toolchain.swim.async;

import java.net.InetSocketAddress;
import java.util.UUID;

public interface EventLoop {
    <T extends DatagramBindChannel> T bind(final InetSocketAddress address, final DatagramBindListener<T> listener)
            throws BindException;

    <T extends DatagramBindChannel> T bind(final String string, final int i, final DatagramBindListener<T> listener)
            throws BindException;

    void schedule(final long delay, final Task task);

    long now();

    UUID uuid();
}
