package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.Data;

@Data
public class Node {
    private final InetSocketAddress address;

    public Node(InetSocketAddress address) {
        this.address = address;
    }
}
