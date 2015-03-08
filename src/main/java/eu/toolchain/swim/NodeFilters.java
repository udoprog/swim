package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.Arrays;

public final class NodeFilters {
    public static NodeFilter state(NodeState state) {
        return new NodeFilter.State(state);
    }

    public static NodeFilter address(InetSocketAddress address) {
        return new NodeFilter.Address(address);
    }

    public static NodeFilter not(NodeFilter delegate) {
        return new NodeFilter.Not(delegate);
    }

    public static NodeFilter or(NodeFilter... delegates) {
        return new NodeFilter.Or(Arrays.asList(delegates));
    }

    public static NodeFilter and(NodeFilter... delegates) {
        return new NodeFilter.And(Arrays.asList(delegates));
    }

    public static NodeFilter any() {
        return new NodeFilter.Any();
    }

    public static NodeFilter younger(long age) {
        return new NodeFilter.Younger(age);
    }
}
