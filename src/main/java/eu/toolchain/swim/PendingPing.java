package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.Data;

@Data
public class PendingPing implements PendingOperation {
    private final long started;
    private final InetSocketAddress target;
    private final boolean fromRequest;
}
