package dev.eatsteak.jchatroom.client;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class TerminalChatClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RECEIVER_JOIN_TIMEOUT = Duration.ofSeconds(1);
    private static final int EVENT_POLL_MILLIS = 30;
    private static final int MAX_INPUT_CODE_POINTS = 1024;

    private final ClientConfig config;

    TerminalChatClient(ClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    void run() throws IOException, InterruptedException {
        BlockingQueue<ClientEvent> events = new LinkedBlockingQueue<>();
        AtomicBoolean connected = new AtomicBoolean(false);
        Socket socket = new Socket();
        ChatScreen screen = new ChatScreen(config);
        Thread receiver = null;

        try {
            socket.connect(
                    new InetSocketAddress(config.host(), config.port()),
                    Math.toIntExact(CONNECT_TIMEOUT.toMillis())
            );
            socket.setTcpNoDelay(true);
            connected.set(true);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            );
            receiver = startReceiver(reader, events, connected, socket);

            screen.start();
            screen.addLine(ServerLinePresentation.system(ClientApplication.startupMessage(config)));
            screen.setStatus("Connected to " + config.address());
            screen.render();

            runEventLoop(screen, writer, socket, events, connected);
        } finally {
            connected.set(false);
            closeQuietly(socket);
            if (receiver != null) {
                receiver.join(RECEIVER_JOIN_TIMEOUT.toMillis());
            }
            screen.stopQuietly();
        }
    }

    private void runEventLoop(
            ChatScreen screen,
            BufferedWriter writer,
            Socket socket,
            BlockingQueue<ClientEvent> events,
            AtomicBoolean connected
    ) throws IOException, InterruptedException {
        boolean running = true;
        while (running) {
            boolean changed = processEvents(screen, socket, events, connected);

            KeyStroke key;
            while ((key = screen.pollInput()) != null) {
                running = handleKey(screen, writer, socket, connected, key);
                changed = true;
                if (!running) {
                    break;
                }
            }

            if (changed) {
                screen.render();
            }
            TimeUnit.MILLISECONDS.sleep(EVENT_POLL_MILLIS);
        }
    }

    private boolean processEvents(
            ChatScreen screen,
            Socket socket,
            BlockingQueue<ClientEvent> events,
            AtomicBoolean connected
    ) {
        boolean changed = false;
        ClientEvent event;
        while ((event = events.poll()) != null) {
            changed = true;
            if (event.type() == ClientEventType.SERVER_LINE) {
                screen.addLine(event.line());
                if (event.line().serverShutdown()) {
                    connected.set(false);
                    closeQuietly(socket);
                    screen.setConnected(false);
                    screen.setStatus(event.line().shutdownStatus() + ". Press Esc or Ctrl-Q to exit.");
                }
            } else {
                connected.set(false);
                screen.setConnected(false);
                screen.setStatus(event.message() + ". Press Esc or Ctrl-Q to exit.");
            }
        }
        return changed;
    }

    private boolean handleKey(
            ChatScreen screen,
            BufferedWriter writer,
            Socket socket,
            AtomicBoolean connected,
            KeyStroke key
    ) {
        KeyType keyType = key.getKeyType();
        if (keyType == KeyType.EOF || keyType == KeyType.Escape) {
            requestExit(writer, socket, connected);
            return false;
        }
        if (keyType == KeyType.Character && isExitChord(key)) {
            requestExit(writer, socket, connected);
            return false;
        }
        if (keyType == KeyType.Enter) {
            sendCurrentInput(screen, writer, connected);
            return true;
        }
        if (keyType == KeyType.Backspace) {
            screen.backspaceInput();
            return true;
        }
        if (keyType == KeyType.Character) {
            appendInput(screen, key);
            return true;
        }
        return true;
    }

    private void sendCurrentInput(
            ChatScreen screen,
            BufferedWriter writer,
            AtomicBoolean connected
    ) {
        String line = screen.consumeInput();
        if (line.isEmpty()) {
            return;
        }
        if (!connected.get()) {
            screen.setStatus("Disconnected. Press Esc or Ctrl-Q to exit.");
            return;
        }

        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            connected.set(false);
            screen.setConnected(false);
            screen.setStatus("Send failed: " + exception.getMessage() + ". Press Esc or Ctrl-Q to exit.");
            return;
        }

        screen.addLine(ServerLinePresentation.sentCommand(line));
        if ("QUIT".equals(line)) {
            screen.setStatus("QUIT sent. Waiting for server close or press Esc to exit.");
        } else {
            screen.setStatus("Sent line to " + config.address());
        }
    }

    private void appendInput(ChatScreen screen, KeyStroke key) {
        Character character = key.getCharacter();
        if (character == null || Character.isISOControl(character)) {
            return;
        }
        if (!screen.appendInput(character)) {
            screen.setStatus("Input limit is " + MAX_INPUT_CODE_POINTS + " characters.");
        }
    }

    private boolean isExitChord(KeyStroke key) {
        Character character = key.getCharacter();
        if (character == null || !key.isCtrlDown()) {
            return false;
        }
        return character == 'q' || character == 'Q' || character == 'c' || character == 'C';
    }

    private void requestExit(BufferedWriter writer, Socket socket, AtomicBoolean connected) {
        if (connected.getAndSet(false)) {
            try {
                writer.write("QUIT");
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
                // The exit path closes the socket below even if QUIT cannot be flushed.
            }
        }
        closeQuietly(socket);
    }

    private Thread startReceiver(
            BufferedReader reader,
            BlockingQueue<ClientEvent> events,
            AtomicBoolean connected,
            Closeable socket
    ) {
        Thread thread = new Thread(() -> receiveLines(reader, events, connected, socket), "jchat-client-receiver");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void receiveLines(
            BufferedReader reader,
            BlockingQueue<ClientEvent> events,
            AtomicBoolean connected,
            Closeable socket
    ) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                ServerLinePresentation presentation = ServerLinePresentation.fromServerLine(line);
                events.offer(ClientEvent.serverLine(presentation));
                if (presentation.serverShutdown()) {
                    if (connected.getAndSet(false)) {
                        closeQuietly(socket);
                    }
                    return;
                }
            }
            if (connected.getAndSet(false)) {
                events.offer(ClientEvent.disconnected("Disconnected by server"));
            }
        } catch (IOException exception) {
            if (connected.getAndSet(false)) {
                events.offer(ClientEvent.disconnected("Connection closed: " + exception.getMessage()));
            }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Closing is best-effort on TUI shutdown and server disconnect.
        }
    }

    private enum ClientEventType {
        SERVER_LINE,
        DISCONNECTED
    }

    private record ClientEvent(ClientEventType type, ServerLinePresentation line, String message) {
        private static ClientEvent serverLine(ServerLinePresentation line) {
            return new ClientEvent(ClientEventType.SERVER_LINE, line, "");
        }

        private static ClientEvent disconnected(String message) {
            return new ClientEvent(ClientEventType.DISCONNECTED, null, message);
        }
    }

    private static final class ChatScreen {
        private static final int MAX_LOG_LINES = 500;
        private static final int MIN_ROWS_FOR_LOG = 6;
        private static final int LOG_LABEL_WIDTH = 8;

        private final ClientConfig config;
        private final List<ServerLinePresentation> logLines = new ArrayList<>();
        private final StringBuilder input = new StringBuilder();

        private Screen screen;
        private String status;
        private boolean connected = true;

        private ChatScreen(ClientConfig config) {
            this.config = config;
            status = "Connecting to " + config.address();
        }

        private void start() throws IOException {
            screen = new DefaultTerminalFactory().createScreen();
            screen.startScreen();
        }

        private void stopQuietly() {
            if (screen == null) {
                return;
            }
            try {
                screen.stopScreen();
            } catch (IOException ignored) {
                // Terminal restoration is best-effort during shutdown.
            }
        }

        private KeyStroke pollInput() throws IOException {
            return screen.pollInput();
        }

        private void addLine(ServerLinePresentation line) {
            logLines.add(line);
            if (logLines.size() > MAX_LOG_LINES) {
                logLines.remove(0);
            }
        }

        private void setStatus(String status) {
            this.status = Objects.requireNonNull(status, "status");
        }

        private void setConnected(boolean connected) {
            this.connected = connected;
        }

        private boolean appendInput(char character) {
            if (input.codePointCount(0, input.length()) >= MAX_INPUT_CODE_POINTS) {
                return false;
            }
            input.append(character);
            return true;
        }

        private void backspaceInput() {
            if (input.isEmpty()) {
                return;
            }
            int previous = input.offsetByCodePoints(input.length(), -1);
            input.delete(previous, input.length());
        }

        private String consumeInput() {
            String line = input.toString();
            input.setLength(0);
            return line;
        }

        private void render() throws IOException {
            TerminalSize resized = screen.doResizeIfNecessary();
            TerminalSize size = resized == null ? screen.getTerminalSize() : resized;
            int columns = Math.max(1, size.getColumns());
            int rows = Math.max(1, size.getRows());
            TextGraphics graphics = screen.newTextGraphics();

            clear(graphics, columns, rows);
            if (rows < MIN_ROWS_FOR_LOG) {
                renderCompact(graphics, columns, rows);
                screen.refresh();
                return;
            }

            int inputSeparatorRow = rows - 4;
            int inputRow = rows - 3;
            int statusRow = rows - 2;
            int helpRow = rows - 1;

            putPadded(
                    graphics,
                    0,
                    "adv-java-chatroom client | " + connectionLabel(),
                    connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED,
                    columns
            );
            putPadded(graphics, 1, "-".repeat(columns), TextColor.ANSI.WHITE, columns);
            renderLog(graphics, columns, 2, inputSeparatorRow - 1);
            putPadded(graphics, inputSeparatorRow, "-".repeat(columns), TextColor.ANSI.WHITE, columns);
            renderInput(graphics, columns, inputRow);
            putPadded(graphics, statusRow, status, connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED, columns);
            putPadded(
                    graphics,
                    helpRow,
                    "Enter sends. Esc/Ctrl-Q exits and sends QUIT if connected.",
                    TextColor.ANSI.WHITE,
                    columns
            );
            positionCursor(columns, inputRow);

            screen.refresh();
        }

        private void renderCompact(TextGraphics graphics, int columns, int rows) {
            if (rows >= 1) {
                putPadded(graphics, 0, connectionLabel(), connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED, columns);
            }
            if (rows >= 2) {
                putPadded(graphics, 1, status, connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED, columns);
            }
            if (rows >= 3) {
                renderInput(graphics, columns, rows - 1);
                positionCursor(columns, rows - 1);
            }
        }

        private void renderLog(TextGraphics graphics, int columns, int firstRow, int lastRow) {
            if (lastRow < firstRow) {
                return;
            }
            int visibleRows = lastRow - firstRow + 1;
            int start = Math.max(0, logLines.size() - visibleRows);
            int row = firstRow;
            for (ServerLinePresentation line : logLines.subList(start, logLines.size())) {
                renderLogLine(graphics, columns, row++, line);
            }
        }

        private void renderLogLine(TextGraphics graphics, int columns, int row, ServerLinePresentation line) {
            TextColor color = colorFor(line.kind());
            String label = padRight(ServerLinePresentation.fit(line.label(), LOG_LABEL_WIDTH - 1), LOG_LABEL_WIDTH);
            graphics.setForegroundColor(color);
            graphics.putString(0, row, ServerLinePresentation.fit(label, columns));

            if (columns <= LOG_LABEL_WIDTH) {
                return;
            }
            graphics.setForegroundColor(TextColor.ANSI.WHITE);
            String text = ServerLinePresentation.fit(line.text(), columns - LOG_LABEL_WIDTH);
            graphics.putString(LOG_LABEL_WIDTH, row, padRight(text, columns - LOG_LABEL_WIDTH));
        }

        private void renderInput(TextGraphics graphics, int columns, int row) {
            String prompt = connected ? "> " : "! ";
            int inputWidth = Math.max(0, columns - prompt.length());
            String visibleInput = visibleInput(inputWidth);
            graphics.setForegroundColor(connected ? TextColor.ANSI.WHITE : TextColor.ANSI.RED);
            graphics.putString(0, row, ServerLinePresentation.fit(prompt + visibleInput, columns));
        }

        private void positionCursor(int columns, int row) {
            if (!connected) {
                screen.setCursorPosition(null);
                return;
            }
            int promptWidth = 2;
            int inputWidth = Math.max(0, columns - promptWidth);
            int visibleLength = visibleInput(inputWidth).length();
            int cursorColumn = Math.min(columns - 1, promptWidth + visibleLength);
            screen.setCursorPosition(new TerminalPosition(Math.max(0, cursorColumn), row));
        }

        private String visibleInput(int width) {
            if (width <= 0) {
                return "";
            }
            int codePoints = input.codePointCount(0, input.length());
            if (codePoints <= width) {
                return input.toString();
            }
            int start = input.offsetByCodePoints(0, codePoints - width);
            return input.substring(start);
        }

        private String connectionLabel() {
            return (connected ? "Connected " : "Disconnected ") + config.address();
        }

        private static TextColor colorFor(LogLineKind kind) {
            return switch (kind) {
                case OK -> TextColor.ANSI.GREEN;
                case ERR -> TextColor.ANSI.RED;
                case EVENT -> TextColor.ANSI.CYAN;
                case SENT -> TextColor.ANSI.YELLOW;
                case SYSTEM -> TextColor.ANSI.WHITE;
                case SERVER -> TextColor.ANSI.MAGENTA;
            };
        }

        private static void clear(TextGraphics graphics, int columns, int rows) {
            graphics.setBackgroundColor(TextColor.ANSI.BLACK);
            graphics.setForegroundColor(TextColor.ANSI.WHITE);
            String blank = " ".repeat(columns);
            for (int row = 0; row < rows; row++) {
                graphics.putString(0, row, blank);
            }
        }

        private static void putPadded(
                TextGraphics graphics,
                int row,
                String text,
                TextColor color,
                int columns
        ) {
            graphics.setForegroundColor(color);
            graphics.putString(0, row, padRight(ServerLinePresentation.fit(text, columns), columns));
        }

        private static String padRight(String text, int width) {
            if (width <= 0) {
                return "";
            }
            int codePoints = text.codePointCount(0, text.length());
            if (codePoints >= width) {
                return text;
            }
            return text + " ".repeat(width - codePoints);
        }
    }
}
