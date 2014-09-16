package eu.toolchain.swim;

import lombok.Data;

@Data
public class ScheduledOperation implements Comparable<ScheduledOperation> {
    private final long when;
    private final Task task;

    public void run(Scheduler.Session session) {
        this.task.run(session);
    }

    @Override
    public int compareTo(ScheduledOperation o) {
        return Long.compare(when, o.when);
    }
}
