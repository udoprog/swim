package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.List;

import lombok.RequiredArgsConstructor;

public interface NodeFilter {
    public boolean matches(NodeData data);

    @RequiredArgsConstructor
    public static class Not implements NodeFilter {
        private final NodeFilter delegate;

        @Override
        public boolean matches(NodeData peer) {
            return !delegate.matches(peer);
        }
    }

    @RequiredArgsConstructor
    public static class Address implements NodeFilter {
        private final InetSocketAddress address;

        @Override
        public boolean matches(NodeData data) {
            return data.getNode().getAddress().equals(address);
        }
    }

    @RequiredArgsConstructor
    public static class State implements NodeFilter {
        private final NodeState state;

        @Override
        public boolean matches(NodeData data) {
            return data.getState().equals(state);
        }
    }

    @RequiredArgsConstructor
    public static class Or implements NodeFilter {
        private final List<NodeFilter> delegates;

        @Override
        public boolean matches(NodeData data) {
            for (final NodeFilter f : delegates) {
                if (f.matches(data))
                    return true;
            }

            return false;
        }
    }

    @RequiredArgsConstructor
    public static class And implements NodeFilter {
        private final List<NodeFilter> delegates;

        @Override
        public boolean matches(NodeData data) {
            for (final NodeFilter f : delegates) {
                if (!f.matches(data))
                    return false;
            }

            return true;
        }
    }

    @RequiredArgsConstructor
    public static class Any implements NodeFilter {
        @Override
        public boolean matches(NodeData data) {
            return true;
        }
    }
}
