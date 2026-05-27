package dev.eatsteak.jchatroom.server;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class BusinessWorkerPool implements AutoCloseable {
    private static final Duration DEFAULT_SHUTDOWN_GRACE_PERIOD = Duration.ofMillis(500);

    private final ThreadPoolExecutor executor;
    private final Duration shutdownGracePeriod;

    BusinessWorkerPool(ServerConfig config) {
        this(config, DEFAULT_SHUTDOWN_GRACE_PERIOD);
    }

    BusinessWorkerPool(ServerConfig config, Duration shutdownGracePeriod) {
        this.shutdownGracePeriod = requireNonNegative(shutdownGracePeriod);
        executor = new ThreadPoolExecutor(
                config.businessThreads(),
                config.businessThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.maxClients()),
                new NamedThreadFactory("jchat-business"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    void execute(Runnable task) {
        executor.execute(task);
    }

    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownGracePeriod.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    boolean awaitTermination(Duration timeout) throws InterruptedException {
        return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        shutdown();
    }

    private static Duration requireNonNegative(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("shutdownGracePeriod must not be negative");
        }
        return duration;
    }
}
