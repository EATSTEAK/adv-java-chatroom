package dev.eatsteak.jchatroom.client;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;

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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class TerminalChatClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RECEIVER_JOIN_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration LIST_REFRESH_INTERVAL = Duration.ofSeconds(5);
    private static final int EVENT_POLL_MILLIS = 30;

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
            screen.addSystemLine(ClientApplication.startupMessage(config));
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
        long nextListRefreshNanos = Long.MAX_VALUE;
        boolean wasLoggedIn = screen.loggedIn();
        while (running) {
            boolean changed = processEvents(screen, socket, events, connected);
            if (!wasLoggedIn && screen.loggedIn()) {
                nextListRefreshNanos = 0L;
            }
            wasLoggedIn = screen.loggedIn();

            KeyStroke key;
            while ((key = screen.pollInput()) != null) {
                running = handleKey(screen, writer, socket, connected, key);
                changed = true;
                if (!running) {
                    break;
                }
            }

            if (running && connected.get() && screen.loggedIn()) {
                long now = System.nanoTime();
                if (now >= nextListRefreshNanos) {
                    changed |= sendListRefresh(screen, writer, connected);
                    nextListRefreshNanos = now + LIST_REFRESH_INTERVAL.toNanos();
                }
            } else {
                nextListRefreshNanos = Long.MAX_VALUE;
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
                screen.addServerLine(event.line());
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

    private boolean sendListRefresh(
            ChatScreen screen,
            BufferedWriter writer,
            AtomicBoolean connected
    ) {
        if (!sendLine(screen, writer, connected, "LIST ROOMS", false)) {
            return true;
        }
        sendLine(screen, writer, connected, "LIST USERS", false);
        return true;
    }

    private boolean handleKey(
            ChatScreen screen,
            BufferedWriter writer,
            Socket socket,
            AtomicBoolean connected,
            KeyStroke key
    ) {
        KeyType keyType = key.getKeyType();
        if (keyType == KeyType.EOF) {
            requestExit(writer, socket, connected);
            return false;
        }
        if (keyType == KeyType.Character && isExitChord(key)) {
            requestExit(writer, socket, connected);
            return false;
        }
        ClientInteractionResult result = screen.handleInput(key);
        if (result.exitRequested()) {
            requestExit(writer, socket, connected);
            return false;
        }
        if (result.hasStatus()) {
            screen.setStatus(result.status());
        }
        for (ClientCommand command : result.commands()) {
            sendClientCommand(screen, writer, connected, command);
        }
        return true;
    }

    private void sendClientCommand(
            ChatScreen screen,
            BufferedWriter writer,
            AtomicBoolean connected,
            ClientCommand command
    ) {
        if (!connected.get()) {
            screen.setStatus("Disconnected. Press Esc or Ctrl-Q to exit.");
            return;
        }

        if (!sendLine(screen, writer, connected, command.line(), true)) {
            return;
        }
        screen.setStatus(command.successStatus());
    }

    private boolean sendLine(
            ChatScreen screen,
            BufferedWriter writer,
            AtomicBoolean connected,
            String line,
            boolean userVisibleFailure
    ) {
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            connected.set(false);
            screen.setConnected(false);
            String prefix = userVisibleFailure ? "Send failed: " : "List refresh failed: ";
            screen.setStatus(prefix + exception.getMessage() + ". Press Esc or Ctrl-Q to exit.");
            return false;
        }

        screen.addSentCommand(line);
        return true;
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
        private static final int MIN_ROWS_FOR_DASHBOARD = 14;
        private static final int MIN_COLUMNS_FOR_DASHBOARD = 60;
        private static final int LOG_LABEL_WIDTH = 8;

        private final ClientConfig config;
        private final ClientViewState viewState = new ClientViewState();
        private final ClientInteractionState interaction = new ClientInteractionState();

        private Screen screen;
        private String status;
        private boolean connected = true;
        private DashboardLayout lastLayout;

        private ChatScreen(ClientConfig config) {
            this.config = config;
            status = "Connecting to " + config.address();
        }

        private void start() throws IOException {
            screen = new DefaultTerminalFactory()
                    .setMouseCaptureMode(MouseCaptureMode.CLICK)
                    .createScreen();
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

        private void addSystemLine(String message) {
            viewState.addSystemLine(message);
        }

        private void addServerLine(ServerLinePresentation line) {
            viewState.addServerLine(line);
            interaction.syncSelections(viewState);
        }

        private void addSentCommand(String line) {
            viewState.addSentCommand(line);
        }

        private boolean loggedIn() {
            return viewState.loggedIn();
        }

        private void setStatus(String status) {
            this.status = Objects.requireNonNull(status, "status");
        }

        private void setConnected(boolean connected) {
            this.connected = connected;
        }

        private ClientInteractionResult handleInput(KeyStroke key) {
            if (key instanceof MouseAction mouseAction) {
                return handleMouse(mouseAction);
            }
            return interaction.handleKey(key, viewState, config.address());
        }

        private void render() throws IOException {
            TerminalSize resized = screen.doResizeIfNecessary();
            TerminalSize size = resized == null ? screen.getTerminalSize() : resized;
            int columns = Math.max(1, size.getColumns());
            int rows = Math.max(1, size.getRows());
            TextGraphics graphics = screen.newTextGraphics();
            interaction.syncSelections(viewState);

            clear(graphics, columns, rows);
            if (rows < MIN_ROWS_FOR_DASHBOARD || columns < MIN_COLUMNS_FOR_DASHBOARD) {
                lastLayout = null;
                renderCompact(graphics, columns, rows);
                screen.refresh();
                return;
            }

            int inputSeparatorRow = rows - 4;
            int inputRow = rows - 3;
            int statusRow = rows - 2;
            int helpRow = rows - 1;

            int panelTop = 1;
            int panelAreaHeight = inputSeparatorRow - panelTop;
            int rawHeight = Math.max(5, Math.min(10, panelAreaHeight / 3));
            int mainHeight = panelAreaHeight - rawHeight;
            if (mainHeight < 5) {
                lastLayout = null;
                renderCompact(graphics, columns, rows);
                screen.refresh();
                return;
            }

            renderHeader(graphics, columns);
            renderDashboard(graphics, columns, panelTop, mainHeight, rawHeight);
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
                renderHeader(graphics, columns);
            }
            if (rows >= 2) {
                putPadded(graphics, 1, status, connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED, columns);
            }
            if (rows >= 3) {
                renderInput(graphics, columns, rows - 1);
                positionCursor(columns, rows - 1);
            }
        }

        private void renderHeader(TextGraphics graphics, int columns) {
            String username = viewState.username().isBlank() ? "not logged in" : "user " + viewState.username();
            String room = viewState.currentRoom().isBlank() ? "no room" : "room " + viewState.currentRoom();
            putPadded(
                    graphics,
                    0,
                    "adv-java-chatroom client | " + connectionLabel() + " | " + username + " | " + room,
                    connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED,
                    columns
            );
        }

        private void renderDashboard(
                TextGraphics graphics,
                int columns,
                int panelTop,
                int mainHeight,
                int rawHeight
        ) {
            int sideWidth = Math.min(28, Math.max(16, columns / 5));
            int rightWidth = sideWidth;
            int chatWidth = columns - sideWidth - rightWidth;
            Panel roomsPanel = new Panel(0, panelTop, sideWidth, mainHeight);
            Panel chatPanel = new Panel(sideWidth, panelTop, chatWidth, mainHeight);
            Panel usersPanel = new Panel(sideWidth + chatWidth, panelTop, rightWidth, mainHeight);
            Panel rawPanel = new Panel(0, panelTop + mainHeight, columns, rawHeight);
            List<String> roomRows = roomRows();
            List<String> userRows = userRows();
            int roomStart = visibleStart(roomRows, roomsPanel);
            int userStart = visibleStart(userRows, usersPanel);
            lastLayout = new DashboardLayout(roomsPanel, chatPanel, usersPanel, rawPanel, roomStart, userStart);

            renderListPanel(
                    graphics,
                    roomsPanel,
                    "Rooms",
                    roomRows,
                    TextColor.ANSI.CYAN,
                    interaction.activePane() == ClientPane.ROOMS,
                    viewState.rooms().isEmpty() ? -1 : interaction.selectedRoomIndex(),
                    roomStart
            );
            renderChatPanel(graphics, chatPanel);
            renderListPanel(
                    graphics,
                    usersPanel,
                    "Users",
                    userRows,
                    TextColor.ANSI.GREEN,
                    interaction.activePane() == ClientPane.USERS,
                    viewState.users().isEmpty() ? -1 : interaction.selectedUserIndex(),
                    userStart
            );
            renderRawLogPanel(graphics, rawPanel);
        }

        private List<String> roomRows() {
            List<String> rooms = viewState.rooms();
            String currentRoom = viewState.currentRoom();
            if (rooms.isEmpty()) {
                return List.of("(none)");
            }
            return rooms.stream()
                    .map(room -> room.equals(currentRoom) ? "* " + room : "  " + room)
                    .toList();
        }

        private List<String> userRows() {
            List<String> users = viewState.users();
            if (users.isEmpty()) {
                return List.of("(none)");
            }
            String username = viewState.username();
            return users.stream()
                    .map(user -> user.equals(username) ? "* " + user : "  " + user)
                    .toList();
        }

        private void renderListPanel(
                TextGraphics graphics,
                Panel panel,
                String title,
                List<String> rows,
                TextColor color,
                boolean active,
                int selectedIndex,
                int start
        ) {
            drawPanel(graphics, panel, title, active);
            int visibleRows = panel.contentHeight();
            int row = panel.y() + 1;
            graphics.setForegroundColor(color);
            for (String value : rows.subList(start, rows.size())) {
                putPanelLine(graphics, panel, row, value, start + row - panel.y() - 1 == selectedIndex);
                row++;
            }
        }

        private void renderChatPanel(TextGraphics graphics, Panel panel) {
            String title = viewState.currentRoom().isBlank() ? "Chat" : "Chat " + viewState.currentRoom();
            drawPanel(graphics, panel, title, interaction.activePane() == ClientPane.CHAT);
            List<ClientViewState.ChatLine> lines = viewState.chatLines();
            if (lines.isEmpty()) {
                graphics.setForegroundColor(TextColor.ANSI.WHITE);
                putPanelLine(graphics, panel, panel.y() + 1, "(no chat yet)", false);
                return;
            }
            int visibleRows = panel.contentHeight();
            int start = Math.max(0, lines.size() - visibleRows);
            int row = panel.y() + 1;
            for (ClientViewState.ChatLine line : lines.subList(start, lines.size())) {
                graphics.setForegroundColor(colorFor(line.kind()));
                putPanelLine(graphics, panel, row++, line.text(), false);
            }
        }

        private void renderRawLogPanel(TextGraphics graphics, Panel panel) {
            drawPanel(graphics, panel, "Raw Log", interaction.activePane() == ClientPane.RAW_LOG);
            List<ServerLinePresentation> lines = viewState.rawLines();
            int visibleRows = panel.contentHeight();
            int start = Math.max(0, lines.size() - visibleRows);
            int row = panel.y() + 1;
            for (ServerLinePresentation line : lines.subList(start, lines.size())) {
                renderRawLogLine(graphics, panel, row++, line);
            }
        }

        private void renderRawLogLine(TextGraphics graphics, Panel panel, int row, ServerLinePresentation line) {
            TextColor color = colorFor(line.kind());
            String label = padRight(ServerLinePresentation.fit(line.label(), LOG_LABEL_WIDTH - 1), LOG_LABEL_WIDTH);
            graphics.setForegroundColor(color);
            putPanelLine(graphics, panel, row, label, false);

            if (panel.contentWidth() <= LOG_LABEL_WIDTH) {
                return;
            }
            graphics.setForegroundColor(TextColor.ANSI.WHITE);
            String text = ServerLinePresentation.fit(line.text(), panel.contentWidth() - LOG_LABEL_WIDTH);
            graphics.putString(
                    panel.x() + 1 + LOG_LABEL_WIDTH,
                    row,
                    padRight(text, panel.contentWidth() - LOG_LABEL_WIDTH)
            );
        }

        private void drawPanel(TextGraphics graphics, Panel panel, String title, boolean active) {
            if (panel.width() < 2 || panel.height() < 2) {
                return;
            }
            graphics.setForegroundColor(active ? TextColor.ANSI.YELLOW : TextColor.ANSI.WHITE);
            String horizontal = "-".repeat(Math.max(0, panel.width() - 2));
            graphics.putString(panel.x(), panel.y(), "+" + horizontal + "+");
            for (int row = panel.y() + 1; row < panel.y() + panel.height() - 1; row++) {
                graphics.putString(panel.x(), row, "|");
                graphics.putString(panel.x() + panel.width() - 1, row, "|");
            }
            graphics.putString(panel.x(), panel.y() + panel.height() - 1, "+" + horizontal + "+");

            if (panel.width() > 4) {
                String label = " " + title + " ";
                String fitted = ServerLinePresentation.fit(label, panel.width() - 4);
                if (active) {
                    graphics.putString(panel.x() + 2, panel.y(), fitted, SGR.BOLD);
                } else {
                    graphics.putString(panel.x() + 2, panel.y(), fitted);
                }
            }
        }

        private void putPanelLine(TextGraphics graphics, Panel panel, int row, String text, boolean selected) {
            if (row <= panel.y() || row >= panel.y() + panel.height() - 1 || panel.contentWidth() <= 0) {
                return;
            }
            String fitted = ServerLinePresentation.fit(text, panel.contentWidth());
            String padded = padRight(fitted, panel.contentWidth());
            if (selected) {
                graphics.putString(panel.x() + 1, row, padded, SGR.REVERSE);
            } else {
                graphics.putString(panel.x() + 1, row, padded);
            }
        }

        private void renderInput(TextGraphics graphics, int columns, int row) {
            String prompt = inputPrompt();
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
            int promptWidth = inputPrompt().length();
            int inputWidth = Math.max(0, columns - promptWidth);
            int visibleLength = visibleInput(inputWidth).length();
            int cursorColumn = Math.min(columns - 1, promptWidth + visibleLength);
            screen.setCursorPosition(new TerminalPosition(Math.max(0, cursorColumn), row));
        }

        private String inputPrompt() {
            if (!connected) {
                return "! ";
            }
            return interaction.inputPrompt(viewState);
        }

        private String visibleInput(int width) {
            if (width <= 0) {
                return "";
            }
            String input = interaction.inputText();
            int codePoints = input.codePointCount(0, input.length());
            if (codePoints <= width) {
                return input;
            }
            int start = input.offsetByCodePoints(0, codePoints - width);
            return input.substring(start);
        }

        private ClientInteractionResult handleMouse(MouseAction mouseAction) {
            if (mouseAction.getActionType() != MouseActionType.CLICK_DOWN
                    && mouseAction.getActionType() != MouseActionType.CLICK_RELEASE) {
                return ClientInteractionResult.none();
            }
            if (lastLayout == null) {
                return ClientInteractionResult.none();
            }

            TerminalPosition position = mouseAction.getPosition();
            int column = position.getColumn();
            int row = position.getRow();
            if (lastLayout.roomsPanel().contains(column, row)) {
                interaction.selectPane(ClientPane.ROOMS);
                selectClickedRow(ClientPane.ROOMS, lastLayout.roomsPanel(), lastLayout.roomStart(), row);
            } else if (lastLayout.chatPanel().contains(column, row)) {
                interaction.selectPane(ClientPane.CHAT);
            } else if (lastLayout.usersPanel().contains(column, row)) {
                interaction.selectPane(ClientPane.USERS);
                selectClickedRow(ClientPane.USERS, lastLayout.usersPanel(), lastLayout.userStart(), row);
            } else if (lastLayout.rawPanel().contains(column, row)) {
                interaction.selectPane(ClientPane.RAW_LOG);
            }
            return ClientInteractionResult.none();
        }

        private void selectClickedRow(ClientPane pane, Panel panel, int start, int row) {
            int relativeRow = row - panel.y() - 1;
            if (relativeRow < 0 || relativeRow >= panel.contentHeight()) {
                return;
            }
            interaction.selectRow(pane, start + relativeRow, viewState);
        }

        private static int visibleStart(List<String> rows, Panel panel) {
            return Math.max(0, rows.size() - panel.contentHeight());
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

        private record Panel(int x, int y, int width, int height) {
            int contentWidth() {
                return Math.max(0, width - 2);
            }

            int contentHeight() {
                return Math.max(0, height - 2);
            }

            boolean contains(int column, int row) {
                return column >= x && column < x + width && row >= y && row < y + height;
            }
        }

        private record DashboardLayout(
                Panel roomsPanel,
                Panel chatPanel,
                Panel usersPanel,
                Panel rawPanel,
                int roomStart,
                int userStart
        ) {
        }
    }
}
