package home.work.policy;

import home.work.pool.CustomThreadPool;
import home.work.pool.RejectedExecutionHandler;

public class DiscardPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable task, CustomThreadPool executor) {
        // Просто игнорируем задачу
    }
}