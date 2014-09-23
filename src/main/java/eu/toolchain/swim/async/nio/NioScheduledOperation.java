package eu.toolchain.swim.async.nio;

import lombok.Data;
import eu.toolchain.swim.async.Task;

@Data
public class NioScheduledOperation {
    private static final Comparator comparator = new Comparator();

    public static Comparator comparator() {
        return comparator;
    }

    private final long when;
    private final Task task;

    public void run() throws Exception {
        task.run();
    }

    public static class Comparator implements java.util.Comparator<NioScheduledOperation> {
        private Comparator() {
        }

        @Override
        public int compare(NioScheduledOperation o1, NioScheduledOperation o2) {
            return Long.compare(o1.when, o2.when);
        }
    }
}
