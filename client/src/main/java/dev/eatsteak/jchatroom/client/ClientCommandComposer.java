package dev.eatsteak.jchatroom.client;

import dev.eatsteak.jchatroom.common.protocol.ProtocolValidator;

import java.util.List;
import java.util.Objects;

final class ClientCommandComposer {
    private ClientCommandComposer() {
    }

    static ClientCommand joinRoom(String room) {
        room = ProtocolValidator.requireRoom(nullToEmpty(room));
        return new ClientCommand("ROOM JOIN " + room, "Room join sent for " + room);
    }

    static ClientCommand leaveRoom(String room) {
        room = ProtocolValidator.requireRoom(nullToEmpty(room));
        return new ClientCommand("ROOM LEAVE " + room, "Room leave sent for " + room);
    }

    static List<ClientCommand> listRefreshCommands() {
        return List.of(
                new ClientCommand("LIST ROOMS", "Room list refresh requested"),
                new ClientCommand("LIST USERS", "User list refresh requested")
        );
    }

    static ClientCommand compose(
            ClientInputMode mode,
            String input,
            String currentRoom,
            String whisperTarget,
            String serverAddress
    ) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(input, "input");
        currentRoom = currentRoom == null ? "" : currentRoom;
        whisperTarget = whisperTarget == null ? "" : whisperTarget;
        serverAddress = serverAddress == null ? "" : serverAddress;

        return switch (mode) {
            case RAW_COMMAND -> rawCommand(input, serverAddress);
            case LOGIN -> loginCommand(input);
            case CREATE_ROOM -> createRoomCommand(input);
            case CHAT_MESSAGE -> chatCommand(input, currentRoom);
            case WHISPER -> whisperCommand(input, whisperTarget);
        };
    }

    private static ClientCommand rawCommand(String input, String serverAddress) {
        String line = ProtocolValidator.requireLine(input);
        if (line.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        String status = "QUIT".equals(line)
                ? "QUIT sent. Waiting for server close or press Esc to exit."
                : "Sent line to " + serverAddress;
        return new ClientCommand(line, status);
    }

    private static ClientCommand loginCommand(String input) {
        String username = ProtocolValidator.requireUsername(input.strip());
        return new ClientCommand("LOGIN " + username, "Login sent for " + username);
    }

    private static ClientCommand createRoomCommand(String input) {
        String room = ProtocolValidator.requireRoom(input.strip());
        return new ClientCommand("ROOM CREATE " + room, "Room create sent for " + room);
    }

    private static ClientCommand chatCommand(String input, String currentRoom) {
        String room = ProtocolValidator.requireRoom(currentRoom);
        String message = requireMessageInput(input);
        return new ClientCommand("MSG " + room + " :" + message, "Message sent to " + room);
    }

    private static ClientCommand whisperCommand(String input, String whisperTarget) {
        String username = ProtocolValidator.requireUsername(whisperTarget);
        String message = requireMessageInput(input);
        return new ClientCommand("WHISPER " + username + " :" + message, "Whisper sent to " + username);
    }

    private static String requireMessageInput(String input) {
        String message = input.strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return ProtocolValidator.requireMessageBody(message);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
