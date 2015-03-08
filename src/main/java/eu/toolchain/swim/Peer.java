package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.ArrayList;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Peer {
    // if this amount of nodes think that this node is dead, we are no longer considering it.
    private static final int RUMOR_LIMIT = 3;

    private final InetSocketAddress address;

    // current node state.
    private final NodeState state;

    // how fresh this data is, only the originating node may increment this value.
    private final long inc;

    private final long updated;

    // what does this node think about other nodes?
    private final ArrayList<Rumor> rumors;

    public Peer(InetSocketAddress address, long now) {
        this(address, NodeState.UNKNOWN, 0, now, new ArrayList<Rumor>());
    }

    public Peer(InetSocketAddress address, NodeState state, long inc, long updated) {
        this(address, state, inc, updated, new ArrayList<Rumor>());
    }

    public Peer state(NodeState state) {
        return new Peer(address, state, inc, updated, rumors);
    }

    public Peer inc(long inc) {
        return new Peer(address, state, inc, updated, rumors);
    }

    public Peer gossip(Rumor rumor) {
        return new Peer(address, state, inc, updated, addRumor(rumor));
    }

    public Peer touch(long now) {
        return new Peer(address, state, inc, now, rumors);
    }

    private ArrayList<Rumor> addRumor(Rumor addition) {
        final ArrayList<Rumor> rumors = new ArrayList<>(this.rumors.size());

        boolean replaced = false;

        for (final Rumor r : this.rumors) {
            if (r.getSource().equals(addition.getSource())) {
                if (addition.getInc() >= inc)
                    rumors.add(addition);

                replaced = true;
                continue;
            }

            rumors.add(r);
        }

        if (!replaced)
            rumors.add(addition);

        return rumors;
    }

    private ArrayList<Rumor> prune(long inc) {
        final ArrayList<Rumor> rumors = new ArrayList<>(this.rumors.size());

        for (final Rumor r : this.rumors) {
            if (r.getInc() >= inc)
                rumors.add(r);
        }

        return rumors;
    }

    public boolean isAlive() {
        if (state == NodeState.DEAD)
            return false;

        if (state == NodeState.ALIVE)
            return true;

        return isRumoredAlive();
    }

    private boolean isRumoredAlive() {
        // not enough rumors to base opinion on.
        if (rumors.size() < RUMOR_LIMIT)
            return false;

        int suspicions = 0;

        for (final Rumor rumor : rumors) {
            // ignore old rumors
            if (rumor.getInc() < this.inc)
                continue;

            if (rumor.getState() == NodeState.UNKNOWN || rumor.getState() == NodeState.DEAD)
                suspicions += 1;

            if (suspicions >= RUMOR_LIMIT)
                return false;
        }

        return true;
    }
}