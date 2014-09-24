package eu.toolchain.swim.async.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.async.BindException;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;
import eu.toolchain.swim.async.Task;

@RequiredArgsConstructor
@Slf4j
public class SimulatorEventLoop implements EventLoop {
    private final Random random;

    private final PriorityQueue<PendingTask> tasks = new PriorityQueue<>(11, PendingTask.comparator());
    private final HashMap<InetSocketAddress, List<ReceivePacket>> receivers = new HashMap<>();
    private final Set<PacketFilter> filters = new HashSet<>();

    private long tick = 0;
    private int packetLoss = 0;

    private long delayLow = 0;
    private long delayHigh = 0;

    @Override
    public void bind(final InetSocketAddress address, final DatagramBindListener listener)
            throws BindException {
        listener.ready(this, new DatagramBindChannel() {
            @Override
            public void send(final InetSocketAddress target, final ByteBuffer output)
                    throws IOException {
                final ByteBuffer slice = output.slice();
                final ByteBuffer buffer = ByteBuffer.allocate(slice.remaining());
                buffer.put(slice);
                buffer.flip();

                final PendingPacket p = buildPacket(address, target, buffer);

                // packet was lost due to a specific filter.
                if (p == null)
                    return;

                schedule(p);
            }

            private void schedule(final PendingPacket p) {
                at(p.getTick(), new Task() {
                    @Override
                    public void run() {
                        final List<ReceivePacket> listeners = receivers.get(p.getDestination());

                        if (listeners == null)
                            return;

                        for (final ReceivePacket listener : listeners) {
                            try {
                                listener.packet(p.getSource(), p.getPacket());
                            } catch (final Exception e) {
                                log.error("listener threw exception", e);
                            }
                        }
                    }
                });
            }

            private PendingPacket buildPacket(InetSocketAddress source,
                    InetSocketAddress destination, ByteBuffer buffer) {
                // simulate global packet loss.
                if (packetLoss > 0 && random.nextInt(100) < packetLoss)
                    return null;

                PendingPacket p = new PendingPacket(tick, source, destination, buffer);

                for (PacketFilter f : filters) {
                    p = f.filter(p);

                    if (p == null)
                        return null;
                }

                p = applyDelay(p, delayLow, delayHigh);

                return p;
            }

            @Override
            public void register(final ReceivePacket listener) {
                List<ReceivePacket> list = receivers.get(address);

                if (list == null) {
                    list = new ArrayList<ReceivePacket>();
                    receivers.put(address, list);
                }

                list.add(listener);
            }

            @Override
            public InetSocketAddress getBindAddress() {
                return address;
            }
        });
    }

    private PendingPacket applyDelay(PendingPacket p, long low, long high) {
        // apply global delay.
        if (high <= 0)
            return p;

        // delay is fixed.
        if (high == low)
            return p.addTicks(high);

        final long delay = low + nextLong(high - low);

        return p.addTicks(delay);
    }

    private long nextLong(long n) {
        // error checking and 2^x checking removed for simplicity.
        long bits, val;

        do {
           bits = (random.nextLong() << 1) >>> 1;
           val = bits % n;
        } while (bits - val + (n-1) < 0L);

        return val;
    }

    @Override
    public void bind(final String host, final int port, final DatagramBindListener listener)
            throws BindException {
        bind(new InetSocketAddress(host, port), listener);
    }

    @Override
    public void schedule(final long delay, final Task task) {
        at(tick + delay, task);
    }

    @Override
    public long now() {
        return tick;
    }

    public void run(final long duration) throws IOException {
        while (true) {
            final PendingTask task = tasks.poll();

            if (task == null || task.getTick() >= duration)
                break;

            tick = task.getTick();

            runTask(task.getTask());
        }
    }

    public void at(final long tick, final Task task) {
        tasks.add(new PendingTask(tick, task));
    }

    private void runTask(final Task task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("failed to run task", e);
        }
    }

    public void cancel(final PacketFilter... filters) {
        for (PacketFilter filter : filters) {
            this.filters.remove(filter);
        }
    }

    public void setPacketLoss(int n) {
        if (n > 100 || n < 0)
            throw new IllegalArgumentException();

        this.packetLoss = n;
    }

    public void setFixedDelay(long delay) {
        this.setRandomDelay(delay, delay);
    }

    public void setRandomDelay(long high) {
        this.setRandomDelay(0, high);
    }

    /**
     * Simulate random delay fluctiations between a low and high point in ticks.
     *
     * A high point of 0 effectively disabled delay.
     *
     * @param low Lowest possible delay.
     * @param high Highest possible delay.
     */
    public void setRandomDelay(long low, long high) {
        if (low < 0 || high < 0 || high <= low)
            throw new IllegalArgumentException(
                    "low and high must be positive or zero, and high is larger or equal to low");

        this.delayLow = low;
        this.delayHigh = high;
    }

    public PacketFilter loss(final InetSocketAddress a, final InetSocketAddress b, final int n) {
        final PacketFilter filter = new PacketFilter() {
            @Override
            public PendingPacket filter(PendingPacket p) {
                if (p.getSource().equals(a) && p.getDestination().equals(b)) {
                    if (random.nextInt(100) < n)
                        return null;
                }

                return p;
            }
        };

        filters.add(filter);
        return filter;
    }

    public PacketFilter delay(final InetSocketAddress a, final InetSocketAddress b, final long delay) {
        if (delay < 0)
            throw new IllegalArgumentException("delay must be positive integer");

        final PacketFilter filter = new PacketFilter() {
            @Override
            public PendingPacket filter(PendingPacket p) {
                if (p.getSource().equals(a) && p.getDestination().equals(b))
                    return p.addTicks(delay);

                return p;
            }
        };

        filters.add(filter);

        return filter;
    }

    public PacketFilter randomDelay(final InetSocketAddress a, final InetSocketAddress b, final long low, final long high) {
        if (low < 0 || high < 0 || high <= low)
            throw new IllegalArgumentException(
                    "low and high must be positive or zero, and high is larger or equal to low");

        final PacketFilter filter = new PacketFilter() {
            @Override
            public PendingPacket filter(PendingPacket p) {
                if (p.getSource().equals(a) && p.getDestination().equals(b))
                    return applyDelay(p, low, high);

                return p;
            }
        };

        filters.add(filter);

        return filter;
    }

    public PacketFilter block(final InetSocketAddress a, final InetSocketAddress b) {
        final PacketFilter filter = new PacketFilter() {
            @Override
            public PendingPacket filter(PendingPacket p) {
                if (p.getSource().equals(a) && p.getDestination().equals(b))
                    return null;

                return p;
            }
        };

        filters.add(filter);

        return filter;
    }
}