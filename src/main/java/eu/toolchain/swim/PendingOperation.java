package eu.toolchain.swim;

import java.net.InetSocketAddress;


public interface PendingOperation {
    public long getStarted();
    public InetSocketAddress getTarget();
}
