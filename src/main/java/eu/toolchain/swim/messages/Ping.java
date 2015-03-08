package eu.toolchain.swim.messages;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class Ping implements Message {
    private final UUID id;
    private final List<Gossip> gossip;
}
