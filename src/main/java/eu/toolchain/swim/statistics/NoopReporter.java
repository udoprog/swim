package eu.toolchain.swim.statistics;

import java.net.InetSocketAddress;
import java.util.UUID;

import eu.toolchain.swim.PendingOperation;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.DirectGossip;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingRequest;

public class NoopReporter implements Reporter {
    @Override
    public void reportExpire(UUID id, PendingOperation pending) {
    }

    @Override
    public void reportNonPendingAck(Ack ack) {
    }

    @Override
    public void reportNoNodeForAck(UUID id, InetSocketAddress addr) {
    }

    @Override
    public void reportMissingPeerForExpire(UUID id, PendingOperation pending) {
    }

    @Override
    public void reportReceivedPing(Ping ping) {
    }

    @Override
    public void reportSentPing(Ping ping) {
    }

    @Override
    public void reportReceivedAck(Ack ack) {
    }

    @Override
    public void reportSentAck(Ack ack) {
    }

    @Override
    public void reportSentPingRequest(PingRequest pingRequest) {
    }

    @Override
    public void reportDirectGossipIncError(DirectGossip g) {
    }
}