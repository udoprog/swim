package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Peer {
    // if this amount of nodes think that this node is dead, we are no longer considering it.
    private static final int RUMOR_LIMIT = 3;

    // what does this node think about other nodes?
    private final Map<InetSocketAddress, Rumor> rumors = new ConcurrentHashMap<>();

    private final boolean seed;

    private final InetSocketAddress address;

    // current node state.
    private final NodeState state;

    // how fresh this data is, only the originating node may increment this value.
    private final long inc;

    // how much has this information been gossiped?
    private final long gossiped;

    private final long updated;

    public Peer(InetSocketAddress address, long now) {
        this(true, address, NodeState.UNKNOWN, 0, 0, now);
    }

    public Peer(InetSocketAddress address, NodeState state, long inc, long updated) {
        this(false, address, state, inc, 0, updated);
    }

    public Peer update(NodeState state, long inc, long now) {
        return new Peer(seed, address, state, inc, 0, now);
    }

    public Peer update(NodeState state, long now) {
        return new Peer(seed, address, state, inc, 0, now);
    }

    public Peer poke(long now) {
        return new Peer(seed, address, state, inc, 0, now);
    }

    public void rumor(InetSocketAddress source, long now, long inc, NodeState state) {
        if (state == NodeState.SUSPECT || state == NodeState.DEAD) {
            this.rumors.put(source, new Rumor(now, inc, state));
        } else {
            this.rumors.remove(source);
        }
    }

    @Data
    public static class Rumor {
        private final long created;
        private final long inc;
        private final NodeState state;
    }

    public boolean isAlive() {
        return (state == NodeState.ALIVE || state == NodeState.SUSPECT) && rumors.size() < RUMOR_LIMIT;
    }
}