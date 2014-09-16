package eu.toolchain.swim.messages;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;

import lombok.Data;

@Data
public class PingReq {
    private final UUID id;
    private final InetSocketAddress target;
    private final Collection<Gossip> payloads;
}
