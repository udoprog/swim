package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.UUID;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import eu.toolchain.async.ResolvableFuture;

@Data
@RequiredArgsConstructor
public class Peer {
    private final UUID id;
    private final InetSocketAddress address;

    // current state according to probing.
    private final NodeState state;

    // how fresh this data is, only the originating node may increment this value.
    private final long inc;

    private final long updated;

    private final ResolvableFuture<Void> confirm;

    public Peer(UUID id, InetSocketAddress address, long now) {
        this(id, address, NodeState.SUSPECT, 0, now, null);
    }

    public Peer state(NodeState state) {
        if (state == NodeState.ALIVE && confirm != null) {
            confirm.cancel();
            return new Peer(id, address, state, inc, updated, null);
        }

        return new Peer(id, address, state, inc, updated, confirm);
    }

    public Peer inc(long inc) {
        return new Peer(id, address, state, inc, updated, confirm);
    }

    public Peer touch(long now) {
        return new Peer(id, address, state, inc, now, confirm);
    }

    public Peer confirm(ResolvableFuture<Void> confirm) {
        if (this.confirm != null)
            this.confirm.cancel();

        return new Peer(id, address, state, inc, updated, confirm);
    }
}