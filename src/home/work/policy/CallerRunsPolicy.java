package home.work.policy;

import home.work.pool.CustomThreadPool;
import home.work.pool.RejectedExecutionHandler;

public class CallerRunsPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable task, CustomThreadPool executor) {
        if (!executor.isShutdown()) {
            task.run();
        }
    }
}