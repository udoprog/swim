package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

import lombok.Data;
import eu.toolchain.swim.async.EventLoop;

public interface NodeFilter {
    public boolean matches(EventLoop loop, Peer data);

    @Data
    public static class Not implements NodeFilter {
        private final NodeFilter delegate;

        @Override
        public boolean matches(EventLoop loop, Peer peer) {
            return !delegate.matches(loop, peer);
        }
    }

    @Data
    public static class ID implements NodeFilter {
        private final UUID id;

        @Override
        public boolean matches(EventLoop loop, Peer peer) {
            return id.equals(peer.getId());
        }
    }

    @Data
    public static class Address implements NodeFilter {
        private final InetSocketAddress address;

        @Override
        public boolean matches(EventLoop loop, Peer data) {
            return data.getAddress().equals(address);
        }
    }

    @Data
    public static class State implements NodeFilter {
        private final NodeState state;

        @Override
        public boolean matches(EventLoop loop, Peer data) {
            return data.getState().equals(state);
        }
    }

    @Data
    public static class Or implements NodeFilter {
        private final List<NodeFilter> delegates;

        @Override
        public boolean matches(EventLoop loop, Peer data) {
            for (final NodeFilter f : delegates) {
                if (f.matches(loop, data))
                    return true;
            }

            return false;
        }
    }

    @Data
    public static class And implements NodeFilter {
        private final List<NodeFilter> delegates;

        @Override
        public boolean matches(EventLoop loop, Peer data) {
            for (final NodeFilter f : delegates) {
                if (!f.matches(loop, data))
                    return false;
            }

            return true;
        }
    }

    @Data
    public static class Any implements NodeFilter {
        @Override
        public boolean matches(EventLoop loop, Peer data) {
            return true;
        }
    }

    @Data
    public static class Younger implements NodeFilter {
        private final long age;

        @Override
        public boolean matches(EventLoop loop, Peer data) {
            return data.getUpdated() + age >= loop.now();
        }
    }
}
