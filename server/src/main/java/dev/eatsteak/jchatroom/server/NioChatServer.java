package dev.eatsteak.jchatroom.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class NioChatServer implements AutoCloseable {
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger clientCount = new AtomicInteger();

    private BusinessWorkerPool businessPool;
    private WorkerReactor[] workers;
    private BossReactor boss;
    private ServerSocketChannel serverChannel;

    public NioChatServer(ServerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }

        try {
            businessPool = new BusinessWorkerPool(config);
            workers = createWorkers();
            for (WorkerReactor worker : workers) {
                worker.start();
            }

            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(config.port()));
            boss = new BossReactor(this, serverChannel, workers);
            boss.start();
        } catch (IOException | RuntimeException exception) {
            shutdown();
            throw exception;
        }
    }

    public int port() throws IOException {
        if (serverChannel == null) {
            throw new IllegalStateException("Server has not been started");
        }
        return ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (boss != null) {
            boss.shutdown();
        } else {
            closeServerChannel();
        }

        if (workers != null) {
            for (WorkerReactor worker : workers) {
                worker.shutdown();
            }
        }

        if (businessPool != null) {
            businessPool.shutdown();
        }
    }

    public void awaitTermination() throws InterruptedException {
        if (boss != null) {
            while (!boss.awaitTermination(Duration.ofDays(1))) {
                // Keep waiting until shutdown is requested and the boss reactor exits.
            }
        }
        if (workers != null) {
            for (WorkerReactor worker : workers) {
                while (!worker.awaitTermination(Duration.ofDays(1))) {
                    // Keep waiting for all worker reactors to exit.
                }
            }
        }
        if (businessPool != null) {
            while (!businessPool.awaitTermination(Duration.ofDays(1))) {
                // Keep waiting for the bounded business pool to exit.
            }
        }
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean stopped = true;

        if (boss != null) {
            stopped &= boss.awaitTermination(remaining(deadline));
        }
        if (workers != null) {
            for (WorkerReactor worker : workers) {
                stopped &= worker.awaitTermination(remaining(deadline));
            }
        }
        if (businessPool != null) {
            stopped &= businessPool.awaitTermination(remaining(deadline));
        }
        return stopped;
    }

    boolean tryReserveClient() {
        while (true) {
            int current = clientCount.get();
            if (current >= config.maxClients()) {
                return false;
            }
            if (clientCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    void releaseClient() {
        clientCount.updateAndGet(current -> Math.max(0, current - 1));
    }

    void executeBusinessTask(Runnable task) {
        businessPool.execute(task);
    }

    boolean isRunning() {
        return running.get();
    }

    void reactorFailed(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        shutdown();
    }

    int clientCount() {
        return clientCount.get();
    }

    private WorkerReactor[] createWorkers() throws IOException {
        WorkerReactor[] created = new WorkerReactor[config.reactorThreads()];
        for (int i = 0; i < created.length; i++) {
            created[i] = new WorkerReactor(this, i, config.outboundQueueCapacity());
        }
        return created;
    }

    private Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(nanos);
    }

    private void closeServerChannel() {
        if (serverChannel == null) {
            return;
        }
        try {
            serverChannel.close();
        } catch (IOException ignored) {
            // Shutdown remains best-effort if the accept channel is already closed.
        }
    }

    @Override
    public void close() {
        shutdown();
        try {
            awaitTermination(CLOSE_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
