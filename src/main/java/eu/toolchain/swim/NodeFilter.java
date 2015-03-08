package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.List;

import lombok.Data;

public interface NodeFilter {
    public boolean matches(Peer data);

    @Data
    public static class Not implements NodeFilter {
        private final NodeFilter delegate;

        @Override
        public boolean matches(Peer peer) {
            return !delegate.matches(peer);
        }
    }

    @Data
    public static class Address implements NodeFilter {
        private final InetSocketAddress address;

        @Override
        public boolean matches(Peer data) {
            return data.getAddress().equals(address);
        }
    }

    @Data
    public static class State implements NodeFilter {
        private final NodeState state;

        @Override
        public boolean matches(Peer data) {
            return data.getState().equals(state);
        }
    }

    @Data
    public static class Or implements NodeFilter {
        private final List<NodeFilter> delegates;

        @Override
        public boolean matches(Peer data) {
            for (final NodeFilter f : delegates) {
                if (f.matches(data))
                    return true;
            }

            return false;
        }
    }

    @Data
    public static class And implements NodeFilter {
        private final List<NodeFilter> delegates;

        @Override
        public boolean matches(Peer data) {
            for (final NodeFilter f : delegates) {
                if (!f.matches(data))
                    return false;
            }

            return true;
        }
    }

    @Data
    public static class Any implements NodeFilter {
        @Override
        public boolean matches(Peer data) {
            return true;
        }
    }

    @Data
    public static class Younger implements NodeFilter {
        private final long now;
        private final long age;

        @Override
        public boolean matches(Peer data) {
            return data.getUpdated() + age >= now;
        }
    }
}
