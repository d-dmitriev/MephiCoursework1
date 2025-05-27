package home.work.policy;

import home.work.pool.CustomThreadPool;
import home.work.pool.RejectedExecutionHandler;

import java.util.concurrent.RejectedExecutionException;

public class AbortPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable task, CustomThreadPool executor) {
        throw new RejectedExecutionException("Task rejected: " + task);
    }
}