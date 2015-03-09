package eu.toolchain.swim.async.nio;

import java.util.PriorityQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.ResolvableFuture;
import eu.toolchain.swim.async.EventLoop;
import eu.toolchain.swim.async.Task;

/**
 * A scheduler which uses ideal time to execute and schedule it's task.
 *
 * This guarantees that scheduled events with even intervals will be fired off evenly.
 *
 * @author udoprog
 */
@Slf4j
@RequiredArgsConstructor
public class NioScheduler {
    /**
     * The number of milliseconds of granularity (error) that this scheduler allows.
     */
    private final EventLoop loop;
    private final long granularity;
    private final AsyncFramework async;

    private final PriorityQueue<NioScheduledOperation> tasks = new PriorityQueue<>(1000,
            NioScheduledOperation.comparator());

    public ResolvableFuture<Void> schedule(final long delay, final Task task) {
        long when = loop.now() + delay;
        when = when - (when % granularity);
        final ResolvableFuture<Void> future = async.future();
        tasks.add(new NioScheduledOperation(when, task, future));
        return future;
    }

    public Long next(final long now) {
        final NioScheduledOperation operation = tasks.peek();

        if (operation == null)
            return null;

        return operation.getWhen() - now;
    }

    public void pop(long now) {
        while (true) {
            final NioScheduledOperation check = tasks.peek();

            System.out.println(check);

            if (check == null || check.getWhen() > now)
                break;

            final NioScheduledOperation run = tasks.poll();

            // not ready
            if (!run.getFuture().resolve(null))
                continue;

            try {
                run.getTask().run();
            } catch (Exception e) {
                log.error("failed to run task", e);
            }
        }
    }
}
