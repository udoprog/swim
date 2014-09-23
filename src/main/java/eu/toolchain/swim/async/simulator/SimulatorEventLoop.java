package eu.toolchain.swim.async.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.GossipService;
import eu.toolchain.swim.async.BindException;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;
import eu.toolchain.swim.async.Task;

@Slf4j
public class SimulatorEventLoop implements EventLoop {
    private final PriorityQueue<PendingTask> tasks= new PriorityQueue<>(11, PendingTask.comparator());
    private final HashMap<InetSocketAddress, List<ReceivePacket>> receivers = new HashMap<>();
    private long tick = 0;

    @Override
    public void bindUDP(final InetSocketAddress address, final DatagramBindListener listener)
            throws BindException {
        listener.ready(this, new DatagramBindChannel() {

            @Override
            public void send(final InetSocketAddress target, final ByteBuffer output)
                    throws IOException {
                final ByteBuffer slice = output.slice();
                final ByteBuffer buffer = ByteBuffer.allocate(slice.remaining());
                buffer.put(slice);

                at(tick + 1, new Runnable() {

                    @Override
                    public void run() {
                        final List<ReceivePacket> listeners = receivers.get(target);

                        if (listeners == null) {
                            return;
                        }

                        for (final ReceivePacket listener : listeners) {
                            try {
                                listener.packet(address, buffer);
                            } catch (final Exception e) {
                                log.error("listener threw exception", e);
                            }
                        }
                    }
                });

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
    public void bindUDP(final String string, final int i, final GossipService gossipService)
            throws BindException {
        // TODO Auto-generated method stub

    }

    @Override
    public void schedule(final long delay, final Task task) {
        // TODO Auto-generated method stub

    }

    public void run(final long duration) throws IOException {
        for (tick = 0; tick < duration; tick++) {
            while (true) {
                final PendingTask task = tasks.peek();

                if (task == null || task.getTick() != tick) {
                    break;
                }

                tasks.poll().getTask().run();
            }
        }
    }

    public void at(final long i, final Runnable task) {
        tasks.add(new PendingTask(i, task));
    }

}
