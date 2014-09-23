package eu.toolchain.swim.async.nio;

import java.util.PriorityQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final long granularity;
    private final EventLoop loop;

    private final PriorityQueue<NioScheduledOperation> tasks = new PriorityQueue<>(11, NioScheduledOperation.comparator());

    public void schedule(final long delay, final Task task) {
        long when = loop.now() + delay;
        when = when - (when % granularity);
        tasks.add(new NioScheduledOperation(when, task));
    }

    public Long next(final long now) {
        final NioScheduledOperation operation =  tasks.peek();

        if (operation == null)
            return null;

        return operation.getWhen() - now;
    }

    public Task pop(long now) {
        while (true) {
            final NioScheduledOperation check =  tasks.peek();

            if (check == null || check.getWhen() > now)
                return null;

            final NioScheduledOperation run = tasks.poll();

            try {
                run.getTask().run();
            } catch (Exception e) {
                log.error("failed to run task", e);
            }
        }
    }
}
