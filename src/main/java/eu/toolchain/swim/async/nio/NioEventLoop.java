package eu.toolchain.swim.async.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import eu.toolchain.swim.async.BindException;
import eu.toolchain.swim.async.DatagramBindChannel;
import eu.toolchain.swim.async.DatagramBindListener;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.ReceivePacket;
import eu.toolchain.swim.async.Task;

@Slf4j
public class NioEventLoop implements EventLoop {
    private final HashMap<DatagramChannel, List<ReceivePacket>> receivers = new HashMap<>();
    private final NioScheduler scheduler;

    private final ByteBuffer input = ByteBuffer.allocate(0xFFFF);

    public NioEventLoop() {
        this.scheduler = new NioScheduler(100, this);
    }

    @Override
    public void bind(final InetSocketAddress address, final DatagramBindListener listener)
            throws BindException {
        final DatagramChannel channel;

        try {
            channel = DatagramChannel.open();
            channel.socket().bind(address);
            channel.configureBlocking(false);
        } catch (final IOException e) {
            throw new BindException("failed to bind UDP listener", e);
        }

        listener.ready(this, new DatagramBindChannel() {
            @Override
            public void send(final InetSocketAddress target, final ByteBuffer output) throws IOException {
                channel.send(output, target);
            }

            @Override
            public void register(final ReceivePacket listener) {
                List<ReceivePacket> list = receivers.get(channel);

                if (list == null) {
                    list = new ArrayList<ReceivePacket>();
                    receivers.put(channel, list);
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
        scheduler.schedule(delay, task);
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    public void run() throws IOException {
        final Selector selector = Selector.open();

        for (final DatagramChannel channel : receivers.keySet()) {
            channel.register(selector, SelectionKey.OP_READ);
        }

        while (true) {
            final Set<SelectionKey> selected = select(selector);

            if (selected == null)
                break;

            for (final SelectionKey key : selected) {
                if (!key.isValid())
                    continue;

                if (key.isReadable()) {
                    read(key);
                    continue;
                }
            }

            selected.clear();
        }

        log.info("Shutting down");
    }

    private Set<SelectionKey> select(final Selector selector) throws IOException {
        while (true) {
            final long now = System.currentTimeMillis();

            final Long next = scheduler.next(now);

            final long sleep;

            if (next != null) {
                sleep = Math.max(0, next);

                if (sleep == 0) {
                    scheduler.pop(now);
                    continue;
                }
            } else {
                sleep = 0;
            }

            final int keys = selector.select(sleep);

            // timed out.
            if (keys == 0) {
                scheduler.pop(now + sleep);
                continue;
            }

            final Set<SelectionKey> selected = selector.selectedKeys();

            if (selected.isEmpty())
                continue;

            return selected;
        }
    }

    private void read(final SelectionKey key) throws IOException {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        final List<ReceivePacket> listeners = receivers.get(channel);

        while (true) {
            input.clear();

            final InetSocketAddress source = (InetSocketAddress) channel.receive(input);

            if (source == null)
                break;

            input.flip();

            if (listeners == null) {
                continue;
            }

            for (final ReceivePacket listener : listeners) {
                try {
                    listener.packet(source, input.slice());
                } catch (final Exception e) {
                    log.error("listener threw an exception", e);
                }
            }
        }
    }
}
