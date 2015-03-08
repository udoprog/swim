package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.Data;

@Data
public class Rumor {
    private final long when;
    private final InetSocketAddress source;
    private final long inc;
    private final NodeState state;
}