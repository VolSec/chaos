package chaos.sim;


import java.util.concurrent.*;


public class BlockingExecutor {

    private final Executor executor;
    private final Semaphore semaphore;

    public BlockingExecutor(int queueSize, int corePoolSize, int maxPoolSize, int keepAliveTime, TimeUnit unit, ThreadFactory factory) {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
        this.executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, queue, factory);
        this.semaphore = new Semaphore(queueSize + maxPoolSize);
    }

    private void execImpl(final Runnable command) throws InterruptedException {
        semaphore.acquire();
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        command.run();
                    } finally {
                        semaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // will never be thrown with an unbounded buffer (LinkedBlockingQueue)
            semaphore.release();
            throw e;
        }
    }

    public void execute(Runnable command) throws InterruptedException {
        execImpl(command);
    }
}

