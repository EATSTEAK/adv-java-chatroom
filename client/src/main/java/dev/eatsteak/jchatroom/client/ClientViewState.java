package dev.eatsteak.jchatroom.client;

import dev.eatsteak.jchatroom.common.protocol.ProtocolLine;
import dev.eatsteak.jchatroom.common.protocol.ProtocolParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class ClientViewState {
    private static final int MAX_CHAT_LINES = 500;
    private static final int MAX_RAW_LINES = 500;

    private final List<String> rooms = new ArrayList<>();
    private final List<String> users = new ArrayList<>();
    private final List<ChatLine> chatLines = new ArrayList<>();
    private final List<ServerLinePresentation> rawLines = new ArrayList<>();

    private boolean loggedIn;
    private String username = "";
    private String currentRoom = "";

    void addSystemLine(String message) {
        addRawLine(ServerLinePresentation.system(message));
    }

    void addSentCommand(String line) {
        addRawLine(ServerLinePresentation.sentCommand(line));
    }

    void addServerLine(ServerLinePresentation line) {
        Objects.requireNonNull(line, "line");
        addRawLine(line);
        applyServerState(line.text());
    }

    boolean loggedIn() {
        return loggedIn;
    }

    String username() {
        return username;
    }

    String currentRoom() {
        return currentRoom;
    }

    List<String> rooms() {
        return List.copyOf(rooms);
    }

    List<String> users() {
        return List.copyOf(users);
    }

    List<ChatLine> chatLines() {
        return List.copyOf(chatLines);
    }

    List<ServerLinePresentation> rawLines() {
        return List.copyOf(rawLines);
    }

    private void applyServerState(String rawLine) {
        ProtocolLine line;
        try {
            line = ProtocolParser.parseLine(rawLine);
        } catch (RuntimeException exception) {
            return;
        }

        List<String> arguments = line.arguments();
        if ("OK".equals(line.command())) {
            applyOk(arguments);
            return;
        }
        if ("EVENT".equals(line.command())) {
            applyEvent(arguments, line.trailingText().orElse(""));
        }
    }

    private void applyOk(List<String> arguments) {
        if (arguments.isEmpty()) {
            return;
        }

        if ("LOGIN".equals(arguments.get(0)) && arguments.size() >= 2) {
            loggedIn = true;
            username = arguments.get(1);
            rememberUser(username);
            return;
        }

        if (arguments.size() >= 2 && "LIST".equals(arguments.get(0))) {
            if ("ROOMS".equals(arguments.get(1))) {
                replaceList(rooms, arguments.subList(2, arguments.size()));
                return;
            }
            if ("USERS".equals(arguments.get(1))) {
                replaceList(users, arguments.subList(2, arguments.size()));
                return;
            }
        }

        if (arguments.size() >= 3 && "ROOM".equals(arguments.get(0))) {
            applyRoomOk(arguments.get(1), arguments.get(2));
        }
    }

    private void applyRoomOk(String action, String room) {
        if ("CREATE".equals(action) || "JOIN".equals(action)) {
            currentRoom = room;
            rememberRoom(room);
            return;
        }
        if ("LEAVE".equals(action) && room.equals(currentRoom)) {
            currentRoom = "";
        }
    }

    private void applyEvent(List<String> arguments, String trailingText) {
        if (arguments.isEmpty()) {
            return;
        }

        String eventType = arguments.get(0);
        if ("MSG".equals(eventType) && arguments.size() >= 3) {
            addChatLine(new ChatLine(
                    LogLineKind.EVENT,
                    "[" + arguments.get(1) + "] " + arguments.get(2) + ": " + trailingText
            ));
            return;
        }
        if ("WHISPER".equals(eventType) && arguments.size() >= 2) {
            addChatLine(new ChatLine(LogLineKind.EVENT, "[whisper] " + arguments.get(1) + ": " + trailingText));
            return;
        }
        if (arguments.size() >= 3 && eventType.startsWith("ROOM_")) {
            applyRoomEvent(eventType, arguments.get(1), arguments.get(2));
        }
    }

    private void applyRoomEvent(String eventType, String room, String actor) {
        if ("ROOM_CREATE".equals(eventType) || "ROOM_JOIN".equals(eventType)) {
            rememberRoom(room);
        }
        if ("ROOM_LEAVE".equals(eventType) && actor.equals(username) && room.equals(currentRoom)) {
            currentRoom = "";
        }
        addChatLine(new ChatLine(
                LogLineKind.SYSTEM,
                "[" + room + "] " + actor + " " + eventType.toLowerCase(Locale.ROOT)
        ));
    }

    private void replaceList(List<String> target, List<String> values) {
        target.clear();
        target.addAll(values);
    }

    private void rememberRoom(String room) {
        if (!rooms.contains(room)) {
            rooms.add(room);
        }
    }

    private void rememberUser(String user) {
        if (!users.contains(user)) {
            users.add(user);
        }
    }

    private void addChatLine(ChatLine line) {
        chatLines.add(line);
        trimToLimit(chatLines, MAX_CHAT_LINES);
    }

    private void addRawLine(ServerLinePresentation line) {
        rawLines.add(line);
        trimToLimit(rawLines, MAX_RAW_LINES);
    }

    private static <T> void trimToLimit(List<T> lines, int limit) {
        while (lines.size() > limit) {
            lines.remove(0);
        }
    }

    record ChatLine(LogLineKind kind, String text) {
        ChatLine {
            kind = Objects.requireNonNull(kind, "kind");
            text = Objects.requireNonNull(text, "text");
        }
    }
}
