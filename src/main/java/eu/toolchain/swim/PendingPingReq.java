package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.UUID;

import lombok.Data;

@Data
public class PendingPingReq implements PendingOperation {
    private final long started;
    private final long expires;
    private final InetSocketAddress target;
    private final UUID pingId;
    private final InetSocketAddress source;
}
