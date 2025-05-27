package home.work.pool;

import home.work.executor.CustomExecutor;
import home.work.metrics.Metrics;
import home.work.policy.AbortPolicy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class CustomThreadPool implements CustomExecutor {
    private static final Logger logger = Logger.getLogger(CustomThreadPool.class.getName());

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final Set<Worker> workers;
    private final List<BlockingQueue<Runnable>> queues;
    private final CustomThreadFactory threadFactory;

    private volatile boolean shutdown = false;
    private final ReentrantLock mainLock = new ReentrantLock();
    private final AtomicInteger currentPoolSize = new AtomicInteger(0);
    private final AtomicInteger activeThreads = new AtomicInteger(0);

    private final RejectedExecutionHandler rejectedHandler;

    private final Metrics metrics = new Metrics();

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime,
                            TimeUnit timeUnit, int queueSize, int minSpareThreads) {
        this(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, queueSize, minSpareThreads, new AbortPolicy());
    }

    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime,
                            TimeUnit timeUnit, int queueSize, int minSpareThreads, RejectedExecutionHandler handler) {
        if (corePoolSize < 0 || maxPoolSize <= 0 || maxPoolSize < corePoolSize ||
                keepAliveTime < 0 || queueSize <= 0 || minSpareThreads < 0) {
            throw new IllegalArgumentException("Invalid thread pool parameters");
        }

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;

        this.workers = Collections.synchronizedSet(new HashSet<>(maxPoolSize));
        this.queues = Collections.synchronizedList(new ArrayList<>(maxPoolSize));
        this.threadFactory = new CustomThreadFactory();

        this.rejectedHandler = handler != null ? handler : new AbortPolicy();

        initializePool();
    }

    private void initializePool() {
        for (int i = 0; i < corePoolSize; i++) {
            createWorker();
        }
        logger.info("[POOL] Initialized with " + corePoolSize + " core threads");
    }

    private void createWorker() {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
        queues.add(queue);
        Worker worker = new Worker(queue);
        Thread thread = threadFactory.newThread(worker);
        worker.setThread(thread);
        workers.add(worker);
        thread.start();
        int size = currentPoolSize.incrementAndGet();
        logger.info("[POOL] Worker created. Current pool size: " + size);
    }

    public boolean isShutdown() {
        return this.shutdown;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }

        FutureTask<T> futureTask = new FutureTask<>(task);
        execute(futureTask); // Используем уже существующий метод execute
        return futureTask;
    }

    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }

        if (shutdown) {
            logger.warning("[REJECT] Task rejected (pool is shutdown): " + task);
            metrics.recordRejectedTask();
            rejectedHandler.rejectedExecution(task, this);
        }

        int activeCount = activeThreads.get();
        int currentSize = currentPoolSize.get();

        // Блокировка только если нужно создать воркер
        if (activeCount >= currentSize && currentSize < maxPoolSize) {
            mainLock.lock();
            try {
                if (activeThreads.get() >= currentPoolSize.get() && currentPoolSize.get() < maxPoolSize) {
                    createWorker();
                }
            } finally {
                mainLock.unlock();
            }
        }

        BlockingQueue<Runnable> targetQueue = getTargetQueue();

        if (!targetQueue.offer(task)) {
            logger.warning("[REJECT] Task rejected (queue full): " + task);
            metrics.recordRejectedTask();
            rejectedHandler.rejectedExecution(task, this);
        } else {
            metrics.recordQueueSize(targetQueue.size());
            logger.info("[QUEUE] Task submitted to queue " + queues.indexOf(targetQueue) + ": " + task);
        }
    }

    // Используем стратегию выбора очереди с наименьшим размером
    private BlockingQueue<Runnable> getTargetQueue() {
        BlockingQueue<Runnable> minQueue = queues.getFirst();
        int minSize = minQueue.size();
        for (BlockingQueue<Runnable> q : queues) {
            if (q.size() < minSize) {
                minQueue = q;
                minSize = q.size();
            }
        }
        return minQueue;
    }

    public void shutdown() {
        shutdown = true;
        logger.info("[POOL] Shutdown initiated");
    }

    public void shutdownNow() {
        shutdown = true;
        logger.info("[POOL] ShutdownNow initiated");
        mainLock.lock();
        try {
            for (Worker worker : workers) {
                worker.interrupt();
            }
        } finally {
            mainLock.unlock();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            boolean done;
            mainLock.lock();
            try {
                done = (activeThreads.get() == 0 && queues.stream().allMatch(Collection::isEmpty));
            } finally {
                mainLock.unlock();
            }
            if (done) return true;
            if (System.nanoTime() >= deadline) return false;
            Thread.sleep(50);
        }
    }

    private class Worker implements Runnable {
        private final BlockingQueue<Runnable> queue;
        private volatile boolean running = true;
        private Thread thread; // ссылка на поток воркера

        public Worker(BlockingQueue<Runnable> queue) {
            this.queue = queue;
        }

        public void setThread(Thread thread) {
            this.thread = thread;
        }

        public void interrupt() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Runnable task = queue.poll(keepAliveTime, timeUnit);
                    if (task != null) {
                        activeThreads.incrementAndGet();
                        try {
                            logger.info("[WORKER " + Thread.currentThread().getName() + "] Running task: " + task);
                            long startTime = System.nanoTime();
                            task.run();
                            long duration = System.nanoTime() - startTime;
                            metrics.recordTaskExecution(duration);
                        } catch (Exception e) {
                            logger.log(java.util.logging.Level.SEVERE, "[WORKER " + Thread.currentThread().getName() + "] Exception in task: " + e.getMessage(), e);
                        } finally {
                            activeThreads.decrementAndGet();
                        }
                        if (!shutdown && shouldAddSpareThread()) {
                            mainLock.lock();
                            try {
                                if (currentPoolSize.get() < maxPoolSize) {
                                    createWorker();
                                    logger.info("[POOL] Spare thread created to maintain minSpareThreads");
                                }
                            } finally {
                                mainLock.unlock();
                            }
                        }
                    } else if (shutdown && queue.isEmpty()) {
                        running = false;
                        logger.info("[WORKER " + Thread.currentThread().getName() + "] Exiting (shutdown & queue empty). Pool size: " + currentPoolSize.get());
                        break;
                    } else if (currentPoolSize.get() > corePoolSize) {
                        logger.info("[WORKER " + Thread.currentThread().getName() + "] Exiting (excess thread). Pool size: " + currentPoolSize.get());
                        break;
                    }
                } catch (InterruptedException e) {
                    logger.info("[WORKER " + Thread.currentThread().getName() + "] Interrupted. Pool size: " + currentPoolSize.get());
                    running = false;
                    break;
                }
            }
            cleanupWorker();
        }

        private void cleanupWorker() {
            mainLock.lock();
            try {
                currentPoolSize.decrementAndGet();
                workers.remove(this);
                queues.remove(queue);
                logger.info("[WORKER " + Thread.currentThread().getName() + "] Terminated. Pool size: " + currentPoolSize.get());
            } finally {
                mainLock.unlock();
            }
        }

        private boolean shouldAddSpareThread() {
            int busy = activeThreads.get();
            int total = currentPoolSize.get();
            int spare = total - busy;
            return spare < minSpareThreads;
        }
    }

    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CustomThreadPool-Worker-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            logger.info("[FACTORY] Created new thread: " + t.getName());
            return t;
        }
    }
}
