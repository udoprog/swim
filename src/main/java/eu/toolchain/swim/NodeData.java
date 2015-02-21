package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import eu.toolchain.swim.async.EventLoop;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = { "address" })
@ToString(exclude = { "loop" })
public class NodeData {
    private final EventLoop loop;
    private final InetSocketAddress address;
    private NodeState state;
    private long inc;
    private long gossip;
    private long lastUpdate;

    public NodeData(EventLoop loop, InetSocketAddress address) {
        this.loop = loop;
        this.address = address;
        this.state = NodeState.ALIVE;
        this.inc = 0;
        this.gossip = 0;
        this.lastUpdate = loop.now();
    }
}
