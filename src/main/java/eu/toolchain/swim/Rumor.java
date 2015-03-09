package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = { "source" })
public class Rumor {
    private final long when;
    private final InetSocketAddress source;
    private final long inc;
    private final NodeState state;
}