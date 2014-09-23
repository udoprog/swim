package eu.toolchain.swim.async.simulator;

import lombok.Data;
import eu.toolchain.swim.async.Task;

@Data
public class PendingTask {
    private static final Comparator comparator = new Comparator();

    private final long tick;
    private final Task task;

    public static Comparator comparator() {
        return comparator;
    }

    public static final class Comparator implements java.util.Comparator<PendingTask> {
        private Comparator() {
        }

        @Override
        public int compare(final PendingTask o1, final PendingTask o2) {
            return Long.compare(o1.tick, o2.tick);
        }
    }
}
