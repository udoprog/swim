package eu.toolchain.swim.async.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.async.BindException;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;
import eu.toolchain.swim.async.Task;

@Slf4j
public class SimulatorEventLoop implements EventLoop {
    private final PriorityQueue<PendingTask> tasks = new PriorityQueue<>(11, PendingTask.comparator());
    private final HashMap<InetSocketAddress, List<ReceivePacket>> receivers = new HashMap<>();
    private final Set<PacketFilter> filters = new HashSet<>();

    private long tick = 0;

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
                PendingPacket p = new PendingPacket(tick, source, destination, buffer);

                for (PacketFilter f : filters) {
                    p = f.filter(p);

                    if (p == null)
                        return null;
                }

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

    public PacketFilter delay(final InetSocketAddress a, final InetSocketAddress b, final long ticks) {
        final PacketFilter filter = new PacketFilter() {
            @Override
            public PendingPacket filter(PendingPacket p) {
                if (p.getSource().equals(a) && p.getDestination().equals(b))
                    return p.addTicks(ticks);

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