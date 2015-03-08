package eu.toolchain.swim.statistics;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;
import eu.toolchain.swim.PendingOperation;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.messages.Ack;
import eu.toolchain.swim.messages.Ping;
import eu.toolchain.swim.messages.PingRequest;

@RequiredArgsConstructor
public class TallyReporter implements Reporter {
    private final AtomicLong expire = new AtomicLong();
    private final AtomicLong nonPendingAck = new AtomicLong();
    private final AtomicLong noNodeForAck = new AtomicLong();
    private final AtomicLong missingPeerForExpire = new AtomicLong();

    private final AtomicLong receivedPings = new AtomicLong();
    private final AtomicLong sentPings = new AtomicLong();
    private final AtomicLong receivedAcks = new AtomicLong();
    private final AtomicLong sentAcks = new AtomicLong();
    private final AtomicLong sentPingRequests = new AtomicLong();

    private final EventLoop loop;

    public long getExpire() {
        return expire.get();
    }

    public long getNonPendingAck() {
        return nonPendingAck.get();
    }

    public long getNoNodeForAck() {
        return noNodeForAck.get();
    }

    public long getMissingPeerForExpire() {
        return missingPeerForExpire.get();
    }

    public long getReceivedPings() {
        return receivedPings.get();
    }

    public long getSentPings() {
        return sentPings.get();
    }

    public long getReceivedAcks() {
        return receivedAcks.get();
    }

    public long getSentAcks() {
        return sentAcks.get();
    }

    public long getSentPingRequest() {
        return sentPingRequests.get();
    }

    @Override
    public void reportExpire(UUID id, PendingOperation pending) {
        expire.incrementAndGet();
    }

    @Override
    public void reportNonPendingAck(Ack ack) {
        nonPendingAck.incrementAndGet();
    }

    @Override
    public void reportNoNodeForAck(UUID id, InetSocketAddress addr) {
        noNodeForAck.incrementAndGet();
    }

    @Override
    public void reportMissingPeerForExpire(UUID id, PendingOperation pending) {
        missingPeerForExpire.incrementAndGet();
    }

    @Override
    public void reportReceivedPing(Ping ping) {
        receivedPings.incrementAndGet();
    }

    @Override
    public void reportSentPing(Ping ping) {
        sentPings.incrementAndGet();
    }

    @Override
    public void reportReceivedAck(Ack ack) {
        receivedAcks.incrementAndGet();
    }

    @Override
    public void reportSentAck(Ack ack) {
        sentAcks.incrementAndGet();
    }

    @Override
    public void reportSentPingRequest(PingRequest pingRequest) {
        sentPingRequests.incrementAndGet();
    }
}