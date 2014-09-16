package eu.toolchain.swim;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(access=AccessLevel.PRIVATE)
public class NodeData {
    private final Node node;
    private final NodeState state;
    private final long inc;
    private final long gossip;
    private final long lastUpdate;

    public NodeData(final Node node) {
        this.node = node;
        this.state = NodeState.ALIVE;
        this.inc = 0;
        this.gossip = 0;
        this.lastUpdate = System.currentTimeMillis();
    }

    public NodeData state(NodeState state) {
        final boolean update = state != this.state;
        final long lastUpdate = update ? System.currentTimeMillis() : this.lastUpdate;
        return new NodeData(node, state, inc, update ? gossip : 0, lastUpdate);
    }

    public NodeData inc(long inc) {
        final boolean update = inc == this.inc;
        final long lastUpdate = update ? System.currentTimeMillis() : this.lastUpdate;
        return new NodeData(node, state, inc, update ? gossip : 0, lastUpdate);
    }
}
