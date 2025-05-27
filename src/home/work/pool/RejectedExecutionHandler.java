package home.work.pool;

public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable task, CustomThreadPool executor);
}
