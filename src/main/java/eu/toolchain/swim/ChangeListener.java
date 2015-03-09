package eu.toolchain.swim;

import java.util.UUID;

public interface ChangeListener {
    public void peerFound(Gossiper gossiper, UUID peer);

    public void peerLost(Gossiper gossiper, UUID peer);
}