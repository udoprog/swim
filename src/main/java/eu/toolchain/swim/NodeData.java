package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import eu.toolchain.swim.async.EventLoop;

@Data
@AllArgsConstructor(access=AccessLevel.PRIVATE)
@EqualsAndHashCode(of={"address"})
@ToString(exclude={"loop"})
public class NodeData {
    private final EventLoop loop;
    private final InetSocketAddress address;
    private final NodeState state;
    private final long inc;
    private final long gossip;
    private final long lastUpdate;

    public NodeData(EventLoop loop, InetSocketAddress address) {
        this.loop = loop;
        this.address = address;
        this.state = NodeState.ALIVE;
        this.inc = 0;
        this.gossip = 0;
        this.lastUpdate = loop.now();
    }

    public NodeData state(NodeState state) {
        final boolean update = state != this.state;
        final long lastUpdate = update ? loop.now() : this.lastUpdate;
        return new NodeData(loop, address, state, inc, update ? gossip : 0, lastUpdate);
    }

    public NodeData inc(long inc) {
        final boolean update = inc == this.inc;
        final long lastUpdate = update ? loop.now() : this.lastUpdate;
        return new NodeData(loop, address, state, inc, update ? gossip : 0, lastUpdate);
    }
}
