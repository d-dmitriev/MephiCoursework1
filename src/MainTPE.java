import java.util.concurrent.*;
import java.util.logging.Logger;

public class MainTPE {
    private static final Logger logger = Logger.getLogger(MainTPE.class.getName());

    private static final int TASK_COUNT = 100;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(80);
        try (ExecutorService executor = new ThreadPoolExecutor(2, 8, 5, TimeUnit.SECONDS, queue)) {

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

            int execute = TASK_COUNT - rejected;
            logger.info("[MAIN] Total time: %d".formatted(endTime - startTime));
            logger.info("[MAIN] Completed tasks: %d".formatted(execute));
            logger.info("[MAIN] Rejected tasks: %d".formatted(rejected));
        }
    }
}
