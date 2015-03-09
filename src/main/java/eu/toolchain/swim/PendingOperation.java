package eu.toolchain.swim;

import java.net.InetSocketAddress;

import eu.toolchain.async.ResolvableFuture;

public interface PendingOperation {
    public long getExpires();

    public InetSocketAddress getTarget();

    public ResolvableFuture<Void> getTimeout();
}
