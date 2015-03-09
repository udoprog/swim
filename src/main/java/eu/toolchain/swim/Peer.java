package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Peer {
    private final InetSocketAddress address;

    // current state according to probing.
    private final NodeState state;

    // how fresh this data is, only the originating node may increment this value.
    private final long inc;

    private final long updated;

    public Peer(InetSocketAddress address, long now) {
        this(address, NodeState.SUSPECT, 0, now);
    }

    public Peer state(NodeState state) {
        return new Peer(address, state, inc, updated);
    }

    public Peer inc(long inc) {
        return new Peer(address, state, inc, updated);
    }

    public Peer touch(long now) {
        return new Peer(address, state, inc, now);
    }
}