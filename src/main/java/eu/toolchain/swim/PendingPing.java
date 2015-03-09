package eu.toolchain.swim;

import java.net.InetSocketAddress;

import lombok.Data;
import eu.toolchain.async.ResolvableFuture;

@Data
public class PendingPing implements PendingOperation {
    private final long expires;
    private final InetSocketAddress target;
    private final ResolvableFuture<Void> timeout;
}