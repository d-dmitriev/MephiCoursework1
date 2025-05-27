package home.work.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong maxQueueSize = new AtomicLong(0);
    private final AtomicLong rejectedTasks = new AtomicLong(0);

    public void recordTaskExecution(long durationNanos) {
        totalTasks.incrementAndGet();
        totalExecutionTime.addAndGet(durationNanos);
    }

    public void recordQueueSize(int size) {
        maxQueueSize.updateAndGet(current -> Math.max(current, size));
    }

    public void recordRejectedTask() {
        rejectedTasks.incrementAndGet();
    }

    public long getTotalTasks() {
        return totalTasks.get();
    }

    public double getAverageExecutionTime() {
        return totalTasks.get() == 0 ? 0 :
                (double) totalExecutionTime.get() / totalTasks.get() / 1_000_000;
    }

    public long getMaxQueueSize() {
        return maxQueueSize.get();
    }

    public long getRejectedTasks() {
        return rejectedTasks.get();
    }

    public void reset() {
        totalTasks.set(0);
        totalExecutionTime.set(0);
        maxQueueSize.set(0);
        rejectedTasks.set(0);
    }
}