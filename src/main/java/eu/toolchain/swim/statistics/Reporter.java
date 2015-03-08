package eu.toolchain.swim.statistics;

import java.net.InetSocketAddress;
import java.util.UUID;

import eu.toolchain.swim.PendingOperation;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingRequest;

public interface Reporter {
    void reportExpire(UUID id, PendingOperation pending);

    void reportNonPendingAck(Ack ack);

    void reportNoNodeForAck(UUID id, InetSocketAddress addr);

    void reportMissingPeerForExpire(UUID id, PendingOperation pending);

    void reportReceivedPing(Ping ping);

    void reportSentPing(Ping ping);

    void reportReceivedAck(Ack ack);

    void reportSentAck(Ack ack);

    void reportSentPingRequest(PingRequest pingRequest);
}
