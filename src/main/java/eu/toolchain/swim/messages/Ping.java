package eu.toolchain.swim.messages;

import java.util.Collection;
import java.util.UUID;

import lombok.Data;

@Data
public class Ping {
    private final UUID id;
    private final Collection<Gossip> payloads;
}
