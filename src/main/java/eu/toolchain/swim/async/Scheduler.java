package eu.toolchain.swim.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Scheduler {
    private final long threshold;

    private final ArrayList<ScheduledOperation> tasks = new ArrayList<>();
    private final LinkedList<ScheduledOperation> newTasks = new LinkedList<>();

    @Data
    public static class Session {
        private final long threshold;
        private final long when;
        private final Scheduler scheduler;
        private final Iterator<ScheduledOperation> iterator;

        public void execute() {
            while (iterator.hasNext()) {
                final ScheduledOperation next = iterator.next();

                if (Math.abs(next.getWhen() - this.when) > threshold)
                    break;

                try {
                    next.run();
                } catch (Exception e) {
                    log.error("failed to execute task", e);
                }

                iterator.remove();
            }
        }
    }

    public void schedule(final long when, final Task task) {
        final long now = System.currentTimeMillis();
        newTasks.add(new ScheduledOperation(now + when, task));
    }

    private void compact() {
        tasks.addAll(newTasks);
        Collections.sort(tasks);
        newTasks.clear();
    }

    public Session next(final long now) {
        compact();

        final Iterator<ScheduledOperation> iterator = tasks.iterator();

        if (!iterator.hasNext())
            return null;

        final long when = iterator.next().getWhen();
        return new Session(threshold, when, this, tasks.iterator());
    }
}
