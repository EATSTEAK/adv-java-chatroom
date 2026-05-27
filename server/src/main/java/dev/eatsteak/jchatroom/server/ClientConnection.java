package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;
import dev.eatsteak.jchatroom.common.protocol.ProtocolException;
import dev.eatsteak.jchatroom.common.protocol.ProtocolFormatter;
import dev.eatsteak.jchatroom.common.protocol.ProtocolValidator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;

final class ClientConnection implements ClientConnectionContext {
    private static final int READ_BUFFER_BYTES = 4096;
    private static final int MAX_UTF8_BYTES_PER_CODE_POINT = 4;
    private static final int MAX_LINE_CODE_POINTS = ProtocolValidator.MAX_LINE_LENGTH;
    private static final int MAX_ACCUMULATED_LINE_BYTES =
            ProtocolValidator.MAX_LINE_LENGTH * MAX_UTF8_BYTES_PER_CODE_POINT + 1;
    private static final String LINE_OR_MESSAGE_TOO_LONG = "line or message too long";
    private static final String INBOUND_QUEUE_FULL = "inbound queue full";
    private static final String OUTBOUND_QUEUE_FULL = "outbound queue full";
    private static final String INVALID_UTF8 = "invalid utf-8";
    private static final String INTERNAL_ERROR = "internal server error";

    private final NioChatServer server;
    private final WorkerReactor reactor;
    private final SocketChannel channel;
    private final int inboundQueueCapacity;
    private final int outboundQueueCapacity;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_BYTES);
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    private final Queue<String> inboundLines = new ArrayDeque<>();
    private final Queue<ByteBuffer> outbound = new ArrayDeque<>();

    private SelectionKey key;
    private long lastActivityNanos = System.nanoTime();
    private volatile boolean closed;
    private boolean closeAfterWrites;
    private boolean commandRunning;

    ClientConnection(
            NioChatServer server,
            WorkerReactor reactor,
            SocketChannel channel,
            int outboundQueueCapacity
    ) {
        this.server = server;
        this.reactor = reactor;
        this.channel = channel;
        this.inboundQueueCapacity = outboundQueueCapacity;
        this.outboundQueueCapacity = outboundQueueCapacity;
    }

    void attach(SelectionKey key) {
        this.key = key;
    }

    void readReady() throws IOException {
        if (closed || closeAfterWrites) {
            return;
        }

        while (true) {
            int read = channel.read(readBuffer);
            if (read == -1) {
                close();
                return;
            }
            if (read == 0) {
                return;
            }

            touch();
            readBuffer.flip();
            while (readBuffer.hasRemaining()) {
                byte current = readBuffer.get();
                if (current == '\n') {
                    String line;
                    try {
                        line = decodeAccumulatedLine();
                    } catch (CharacterCodingException exception) {
                        lineBuffer.reset();
                        failWithErrorAndClose(ProtocolErrorCode.MALFORMED_COMMAND, INVALID_UTF8);
                        return;
                    }
                    lineBuffer.reset();
                    if (isLineTooLong(line)) {
                        failWithErrorAndClose(ProtocolErrorCode.TOO_LONG, LINE_OR_MESSAGE_TOO_LONG);
                        return;
                    }
                    dispatchLine(line);
                    if (closed || closeAfterWrites) {
                        return;
                    }
                } else {
                    lineBuffer.write(current);
                    if (lineBuffer.size() > MAX_ACCUMULATED_LINE_BYTES) {
                        lineBuffer.reset();
                        failWithErrorAndClose(ProtocolErrorCode.TOO_LONG, LINE_OR_MESSAGE_TOO_LONG);
                        return;
                    }
                }
            }
            readBuffer.clear();
        }
    }

    void writeReady() throws IOException {
        if (closed) {
            return;
        }

        while (!outbound.isEmpty()) {
            ByteBuffer buffer = outbound.peek();
            int written = channel.write(buffer);
            if (written > 0) {
                touch();
            }
            if (buffer.hasRemaining()) {
                return;
            }
            outbound.remove();
        }

        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
        if (closeAfterWrites) {
            close();
        }
    }

    void enqueueLine(String line) {
        if (closed || closeAfterWrites) {
            return;
        }

        ByteBuffer encoded;
        try {
            encoded = encodeLine(line);
        } catch (ProtocolException | IllegalArgumentException exception) {
            failWithErrorAndClose(ProtocolErrorCode.INTERNAL_ERROR, INTERNAL_ERROR);
            return;
        }

        if (outbound.size() >= outboundQueueCapacity) {
            failWithErrorAndClose(ProtocolErrorCode.CAPACITY_EXCEEDED, OUTBOUND_QUEUE_FULL);
            return;
        }

        outbound.add(encoded);
        enableWriteInterest();
    }

    void markCloseAfterWrites() {
        if (closed) {
            return;
        }
        closeAfterWrites = true;
        disableReadInterest();
        if (outbound.isEmpty()) {
            close();
            return;
        }
        enableWriteInterest();
    }

    void failWithErrorAndClose(ProtocolErrorCode errorCode, String message) {
        if (closed) {
            return;
        }

        closeAfterWrites = true;
        disableReadInterest();
        inboundLines.clear();
        outbound.clear();
        try {
            outbound.add(encodeLine(ProtocolFormatter.error(errorCode, message)));
        } catch (RuntimeException exception) {
            close();
            return;
        }
        enableWriteInterest();
    }

    void closeIfIdle(long nowNanos, long idleTimeoutNanos) {
        if (!closed && nowNanos - lastActivityNanos >= idleTimeoutNanos) {
            close();
        }
    }

    void commandHandled() {
        commandRunning = false;
        dispatchNextLine();
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (key != null) {
            key.cancel();
        }
        inboundLines.clear();
        outbound.clear();
        closeChannel();
        reactor.remove(this);
        try {
            server.clientClosed(this);
        } finally {
            server.releaseClient();
        }
    }

    @Override
    public void sendLine(String line) {
        reactor.enqueueWrite(this, line);
    }

    @Override
    public void closeAfterWrites() {
        reactor.closeAfterWrites(this);
    }

    @Override
    public void closeWithError(ProtocolErrorCode errorCode, String message) {
        reactor.closeWithError(this, errorCode, message);
    }

    @Override
    public void closeNow() {
        reactor.closeNow(this);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    private void dispatchLine(String line) {
        if (inboundLines.size() >= inboundQueueCapacity) {
            failWithErrorAndClose(ProtocolErrorCode.CAPACITY_EXCEEDED, INBOUND_QUEUE_FULL);
            return;
        }
        inboundLines.add(line);
        dispatchNextLine();
    }

    private void dispatchNextLine() {
        if (closed || closeAfterWrites || commandRunning || inboundLines.isEmpty()) {
            return;
        }

        String line = inboundLines.remove();
        commandRunning = true;
        try {
            server.executeBusinessTask(() -> {
                try {
                    server.handleClientLine(this, line);
                } catch (RuntimeException exception) {
                    closeWithError(ProtocolErrorCode.INTERNAL_ERROR, INTERNAL_ERROR);
                } finally {
                    reactor.commandHandled(this);
                }
            });
        } catch (RuntimeException exception) {
            commandRunning = false;
            failWithErrorAndClose(ProtocolErrorCode.INTERNAL_ERROR, INTERNAL_ERROR);
        }
    }

    private String decodeAccumulatedLine() throws CharacterCodingException {
        byte[] bytes = lineBuffer.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes, 0, length)).toString();
    }

    private boolean isLineTooLong(String line) {
        return line.codePointCount(0, line.length()) > MAX_LINE_CODE_POINTS;
    }

    private ByteBuffer encodeLine(String line) {
        String validated = ProtocolValidator.requireLine(line);
        return ByteBuffer.wrap((validated + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void enableWriteInterest() {
        if (key == null || !key.isValid()) {
            close();
            return;
        }
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    private void disableReadInterest() {
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }
    }

    private void touch() {
        lastActivityNanos = System.nanoTime();
    }

    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Closing is best-effort during reactor shutdown.
        }
    }
}
