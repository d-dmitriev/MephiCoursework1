import home.work.executor.CustomExecutor;
import home.work.metrics.Metrics;
import home.work.pool.CustomThreadPool;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    private static final int TASK_COUNT = 100;

    public static void main(String[] args) throws InterruptedException {
        CustomExecutor executor = new CustomThreadPool(
                2, 8, 5, TimeUnit.SECONDS, 10, 2
        );

        int rejected = 0;

        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= TASK_COUNT; i++) {
            try {
                executor.execute(new WorkerTask(i));
            } catch (RejectedExecutionException e) {
                rejected++;
                logger.warning("[REJECT] Task %d rejected".formatted(i));
            }
        }

        executor.shutdown();
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            // Ждем завершения всех задач
            logger.info("Waiting end tasks...");
        }

        long endTime = System.currentTimeMillis();
        Metrics metrics = executor.getMetrics();

        logger.info("[METRICS] Total tasks executed: %d".formatted(metrics.getTotalTasks()));
        logger.info("[METRICS] Average execution time: %.2f ms".formatted(metrics.getAverageExecutionTime()));
        logger.info("[METRICS] Max queue size observed: %d".formatted(metrics.getMaxQueueSize()));
        logger.info("[METRICS] Rejected tasks: %d".formatted(metrics.getRejectedTasks()));

        int execute = TASK_COUNT - rejected;
        logger.info("[MAIN] Total time: %d".formatted(endTime - startTime));
        logger.info("[MAIN] Completed tasks: %d".formatted(execute));
        logger.info("[MAIN] Rejected tasks: %d".formatted(rejected));
    }
}
