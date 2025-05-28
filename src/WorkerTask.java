import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

class WorkerTask implements Runnable {
    private static final Logger logger = Logger.getLogger(WorkerTask.class.getName());

    private final int taskId;

    public WorkerTask(int taskId) {
        this.taskId = taskId;
    }

    @Override
    public void run() {
        logger.info("[TASK] Task " + taskId + " start running in thread " + Thread.currentThread().getName());
        try {
            // Случайное время выполнения от 1 до 3 секунд
            Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3001));
        } catch (InterruptedException e) {
            logger.warning("[TASK] " + taskId + " interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        logger.info("[TASK] Task " + taskId + " end running in thread " + Thread.currentThread().getName());
    }

    @Override
    public String toString() {
        return "Task-" + taskId;
    }
}
