package eu.toolchain.swim.messages;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class Ack implements Message {
    private final UUID id;
    private final boolean ok;
    private final List<Gossip> gossip;
}