package dev.eatsteak.jchatroom.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;

final class ClientConnection {
    private static final int READ_BUFFER_BYTES = 4096;
    private static final int MAX_PLACEHOLDER_LINE_CHARS = 8192;

    private final NioChatServer server;
    private final WorkerReactor reactor;
    private final SocketChannel channel;
    private final int outboundQueueCapacity;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_BYTES);
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    private final Queue<ByteBuffer> outbound = new ArrayDeque<>();

    private SelectionKey key;
    private boolean closed;

    ClientConnection(
            NioChatServer server,
            WorkerReactor reactor,
            SocketChannel channel,
            int outboundQueueCapacity
    ) {
        this.server = server;
        this.reactor = reactor;
        this.channel = channel;
        this.outboundQueueCapacity = outboundQueueCapacity;
    }

    void attach(SelectionKey key) {
        this.key = key;
    }

    void readReady() throws IOException {
        while (true) {
            int read = channel.read(readBuffer);
            if (read == -1) {
                close();
                return;
            }
            if (read == 0) {
                return;
            }

            readBuffer.flip();
            while (readBuffer.hasRemaining()) {
                byte current = readBuffer.get();
                if (current == '\n') {
                    String line = new String(lineBuffer.toByteArray(), StandardCharsets.UTF_8);
                    lineBuffer.reset();
                    dispatchPlaceholderLine(line);
                } else if (current != '\r') {
                    lineBuffer.write(current);
                    if (lineBuffer.size() > MAX_PLACEHOLDER_LINE_CHARS) {
                        close();
                        return;
                    }
                }
            }
            readBuffer.clear();
        }
    }

    void writeReady() throws IOException {
        while (!outbound.isEmpty()) {
            ByteBuffer buffer = outbound.peek();
            channel.write(buffer);
            if (buffer.hasRemaining()) {
                return;
            }
            outbound.remove();
        }

        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    void enqueueLine(String line) {
        if (closed) {
            return;
        }
        if (outbound.size() >= outboundQueueCapacity) {
            close();
            return;
        }

        outbound.add(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (key != null) {
            key.cancel();
        }
        outbound.clear();
        closeChannel();
        reactor.remove(this);
        server.releaseClient();
    }

    private void dispatchPlaceholderLine(String line) {
        try {
            server.executeBusinessTask(() -> reactor.enqueueWrite(
                    this,
                    "STAGE2:ECHO:" + line + "\n"
            ));
        } catch (RuntimeException exception) {
            close();
        }
    }

    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Closing is best-effort during reactor shutdown.
        }
    }
}
