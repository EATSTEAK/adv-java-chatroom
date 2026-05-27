package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class WorkerReactor implements Runnable {
    private static final long SELECT_TIMEOUT_MILLIS = 250L;

    private final NioChatServer server;
    private final int index;
    private final int outboundQueueCapacity;
    private final long idleTimeoutNanos;
    private final Selector selector;
    private final Thread thread;
    private final ConcurrentLinkedQueue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile String serverShutdownLine;

    WorkerReactor(
            NioChatServer server,
            int index,
            int outboundQueueCapacity,
            int idleTimeoutSeconds
    ) throws IOException {
        this.server = server;
        this.index = index;
        this.outboundQueueCapacity = outboundQueueCapacity;
        idleTimeoutNanos = TimeUnit.SECONDS.toNanos(idleTimeoutSeconds);
        selector = Selector.open();
        thread = new Thread(this, "jchat-worker-reactor-" + index);
        thread.setDaemon(false);
    }

    void start() {
        thread.start();
    }

    void register(SocketChannel channel) {
        if (!running.get()) {
            server.logAccess("CONNECTION_REJECTED", "reason", "reactor_stopped", "remote", remoteAddress(channel));
            closeQuietly(channel);
            server.releaseClient();
            return;
        }

        Runnable task = () -> registerOnReactor(channel);
        pendingTasks.add(task);
        if (!running.get() && pendingTasks.remove(task)) {
            server.logAccess("CONNECTION_REJECTED", "reason", "reactor_stopped", "remote", remoteAddress(channel));
            closeQuietly(channel);
            server.releaseClient();
            return;
        }
        selector.wakeup();
    }

    void enqueueWrite(ClientConnection connection, String line) {
        schedule(() -> connection.enqueueLine(line));
    }

    void closeAfterWrites(ClientConnection connection) {
        schedule(connection::markCloseAfterWrites);
    }

    void closeWithError(ClientConnection connection, ProtocolErrorCode errorCode, String message) {
        schedule(() -> connection.failWithErrorAndClose(errorCode, message));
    }

    void closeNow(ClientConnection connection) {
        schedule(connection::close);
    }

    void commandHandled(ClientConnection connection) {
        schedule(connection::commandHandled);
    }

    void remove(ClientConnection connection) {
        connections.remove(connection);
    }

    void notifyServerShutdown(String line) {
        serverShutdownLine = Objects.requireNonNull(line, "line");
        schedule(() -> {
            for (ClientConnection connection : connections.toArray(ClientConnection[]::new)) {
                connection.sendServerShutdownAndClose(line);
            }
        });
    }

    void shutdown() {
        if (running.compareAndSet(true, false)) {
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
                runPendingTasks();
                selector.select(SELECT_TIMEOUT_MILLIS);
                runPendingTasks();
                processSelectedKeys();
                sweepIdleConnections();
            }
        } catch (IOException | RuntimeException exception) {
            server.reactorFailed(exception);
        } finally {
            runPendingTasks();
            closeConnections();
            closeSelector();
        }
    }

    private void registerOnReactor(SocketChannel channel) {
        if (!running.get() && serverShutdownLine == null) {
            closeQuietly(channel);
            server.releaseClient();
            return;
        }

        try {
            ClientConnection connection = new ClientConnection(server, this, channel, outboundQueueCapacity);
            SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            connection.attach(key);
            key.attach(connection);
            connections.add(connection);
            if (serverShutdownLine != null) {
                connection.sendServerShutdownAndClose(serverShutdownLine);
            }
        } catch (IOException | RuntimeException exception) {
            server.logAccessError("CONNECTION_REGISTER_FAILURE", exception, "remote", remoteAddress(channel));
            closeQuietly(channel);
            server.releaseClient();
        }
    }

    private void runPendingTasks() {
        Runnable task;
        while ((task = pendingTasks.poll()) != null) {
            task.run();
        }
    }

    private void processSelectedKeys() {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            if (!key.isValid()) {
                continue;
            }

            ClientConnection connection = (ClientConnection) key.attachment();
            try {
                if (key.isReadable()) {
                    connection.readReady();
                }
                if (key.isValid() && key.isWritable()) {
                    connection.writeReady();
                }
            } catch (IOException | CancelledKeyException exception) {
                connection.closeDueToException(exception);
            }
        }
    }

    private void sweepIdleConnections() {
        long now = System.nanoTime();
        for (ClientConnection connection : connections.toArray(ClientConnection[]::new)) {
            connection.closeIfIdle(now, idleTimeoutNanos);
        }
    }

    private void schedule(Runnable task) {
        if (!running.get()) {
            return;
        }

        pendingTasks.add(task);
        if (!running.get()) {
            pendingTasks.remove(task);
            return;
        }
        selector.wakeup();
    }

    private void closeConnections() {
        for (ClientConnection connection : connections.toArray(ClientConnection[]::new)) {
            connection.closeAfterDrainingQueuedWrites();
        }
    }

    private void closeSelector() {
        try {
            selector.close();
        } catch (IOException ignored) {
            // Selector close is best-effort after all channels are closed.
        }
    }

    private void closeQuietly(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Registration failures are already handled by releasing the client slot.
        }
    }

    private String remoteAddress(SocketChannel channel) {
        try {
            return String.valueOf(channel.getRemoteAddress());
        } catch (IOException exception) {
            return "unknown";
        }
    }

    @Override
    public String toString() {
        return "WorkerReactor[" + index + ']';
    }
}
