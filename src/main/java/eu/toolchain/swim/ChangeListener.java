package eu.toolchain.swim;

import java.net.InetSocketAddress;

public interface ChangeListener {
    public void peerFound(InetSocketAddress peer);

    public void peerLost(InetSocketAddress peer);

    public static final ChangeListener NOOP = new ChangeListener() {
        @Override
        public void peerFound(InetSocketAddress peer) {
        }

        @Override
        public void peerLost(InetSocketAddress peer) {
        }
    };
}