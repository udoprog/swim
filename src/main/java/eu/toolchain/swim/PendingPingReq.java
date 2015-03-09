package eu.toolchain.swim;

import java.net.InetSocketAddress;
import java.util.UUID;

import lombok.Data;
import eu.toolchain.async.ResolvableFuture;

@Data
public class PendingPingReq implements PendingOperation {
    private final long started;
    private final long expires;
    private final InetSocketAddress target;
    private final UUID sourcePingId;
    private final InetSocketAddress source;
    private final ResolvableFuture<Void> timeout;
}
