package eu.toolchain.swim.async;

import java.net.InetSocketAddress;
import java.util.UUID;

import eu.toolchain.async.ResolvableFuture;

public interface EventLoop {
    <T> T bind(final InetSocketAddress address, final DatagramBindListener<T> listener) throws BindException;

    <T> T bind(final String string, final int i, final DatagramBindListener<T> listener) throws BindException;

    ResolvableFuture<Void> schedule(final long delay, final Task task);

    long now();

    UUID uuid();
}
