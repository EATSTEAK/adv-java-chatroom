package dev.eatsteak.jchatroom.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class BossReactor implements Runnable {
    private final NioChatServer server;
    private final ServerSocketChannel serverChannel;
    private final WorkerReactor[] workers;
    private final Selector selector;
    private final Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger nextWorker = new AtomicInteger();

    BossReactor(NioChatServer server, ServerSocketChannel serverChannel, WorkerReactor[] workers) throws IOException {
        this.server = server;
        this.serverChannel = serverChannel;
        this.workers = workers;
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        thread = new Thread(this, "jchat-boss-reactor");
        thread.setDaemon(false);
    }

    void start() {
        thread.start();
    }

    void shutdown() {
        if (running.compareAndSet(true, false)) {
            closeServerChannel();
            selector.wakeup();
        }
    }

    boolean awaitTermination(Duration timeout) throws InterruptedException {
        long timeoutMillis = timeout.toMillis();
        if (timeoutMillis <= 0) {
            return !thread.isAlive();
        }
        thread.join(timeoutMillis);
        return !thread.isAlive();
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                selector.select();
                processSelectedKeys();
            }
        } catch (IOException | RuntimeException exception) {
            server.reactorFailed(exception);
        } finally {
            closeServerChannel();
            closeSelector();
        }
    }

    private void processSelectedKeys() throws IOException {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            if (key.isValid() && key.isAcceptable()) {
                acceptReady();
            }
        }
    }

    private void acceptReady() throws IOException {
        while (running.get()) {
            SocketChannel client = serverChannel.accept();
            if (client == null) {
                return;
            }

            client.configureBlocking(false);
            if (!server.tryReserveClient()) {
                client.close();
                continue;
            }

            workerForNextClient().register(client);
        }
    }

    private WorkerReactor workerForNextClient() {
        int index = Math.floorMod(nextWorker.getAndIncrement(), workers.length);
        return workers[index];
    }

    private void closeServerChannel() {
        try {
            serverChannel.close();
        } catch (IOException ignored) {
            // Server shutdown should continue even if accept channel close reports an error.
        }
    }

    private void closeSelector() {
        try {
            selector.close();
        } catch (IOException ignored) {
            // Selector close is best-effort during shutdown.
        }
    }
}
