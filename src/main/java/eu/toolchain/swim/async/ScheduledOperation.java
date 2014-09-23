package eu.toolchain.swim.async;

import lombok.Data;

@Data
public class ScheduledOperation implements Comparable<ScheduledOperation> {
    private final long when;
    private final Task task;

    public void run() throws Exception {
        task.run();
    }

    @Override
    public int compareTo(final ScheduledOperation o) {
        return Long.compare(when, o.when);
    }
}
