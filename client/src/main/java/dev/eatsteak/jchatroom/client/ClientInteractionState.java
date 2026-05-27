package dev.eatsteak.jchatroom.client;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.List;
import java.util.Objects;

final class ClientInteractionState {
    private static final int MAX_INPUT_CODE_POINTS = 1024;

    private ClientPane activePane = ClientPane.ROOMS;
    private ClientInputMode inputMode = ClientInputMode.LOGIN;
    private final StringBuilder input = new StringBuilder();
    private int selectedRoomIndex;
    private int selectedUserIndex;
    private String whisperTarget = "";

    ClientInteractionResult handleKey(KeyStroke key, ClientViewState viewState, String serverAddress) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(viewState, "viewState");
        syncSelections(viewState);

        if (inputMode != null) {
            return handleInputModeKey(key, viewState, serverAddress);
        }

        return handleNavigationKey(key, viewState, serverAddress);
    }

    void selectPane(ClientPane pane) {
        activePane = Objects.requireNonNull(pane, "pane");
    }

    void selectRow(ClientPane pane, int rowIndex, ClientViewState viewState) {
        selectPane(pane);
        if (pane == ClientPane.ROOMS) {
            selectedRoomIndex = clamp(rowIndex, viewState.rooms().size());
        } else if (pane == ClientPane.USERS) {
            selectedUserIndex = clamp(rowIndex, viewState.users().size());
        }
    }

    void syncSelections(ClientViewState viewState) {
        selectedRoomIndex = clamp(selectedRoomIndex, viewState.rooms().size());
        selectedUserIndex = clamp(selectedUserIndex, viewState.users().size());
        if (viewState.loggedIn() && inputMode == ClientInputMode.LOGIN) {
            cancelInput();
        }
    }

    ClientPane activePane() {
        return activePane;
    }

    ClientInputMode inputMode() {
        return inputMode;
    }

    boolean inputActive() {
        return inputMode != null;
    }

    String inputText() {
        return input.toString();
    }

    int selectedRoomIndex() {
        return selectedRoomIndex;
    }

    int selectedUserIndex() {
        return selectedUserIndex;
    }

    String inputPrompt(ClientViewState viewState) {
        if (inputMode == ClientInputMode.RAW_COMMAND) {
            return "raw> ";
        }
        if (inputMode == ClientInputMode.LOGIN) {
            return "login> ";
        }
        if (inputMode == ClientInputMode.CREATE_ROOM) {
            return "new room> ";
        }
        if (inputMode == ClientInputMode.WHISPER) {
            return "@" + whisperTarget + "> ";
        }
        if (inputMode == ClientInputMode.CHAT_MESSAGE) {
            return roomLabel(viewState) + " msg> ";
        }
        String currentRoom = viewState.currentRoom();
        return currentRoom.isBlank() ? "> " : currentRoom + "> ";
    }

    private ClientInteractionResult handleInputModeKey(
            KeyStroke key,
            ClientViewState viewState,
            String serverAddress
    ) {
        KeyType keyType = key.getKeyType();
        if (keyType == KeyType.Escape) {
            cancelInput();
            return ClientInteractionResult.status("Input cancelled.");
        }
        if (keyType == KeyType.Enter) {
            return submitInput(viewState, serverAddress);
        }
        if (keyType == KeyType.Backspace) {
            backspaceInput();
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.Character) {
            return appendCharacter(key);
        }
        return ClientInteractionResult.none();
    }

    private ClientInteractionResult handleNavigationKey(
            KeyStroke key,
            ClientViewState viewState,
            String serverAddress
    ) {
        KeyType keyType = key.getKeyType();
        if (keyType == KeyType.Escape) {
            return ClientInteractionResult.exit();
        }
        if (keyType == KeyType.Tab) {
            activePane = activePane.next();
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.ReverseTab) {
            activePane = activePane.previous();
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.ArrowLeft) {
            activePane = activePane.previous();
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.ArrowRight) {
            activePane = activePane.next();
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.ArrowUp) {
            moveSelection(-1, viewState);
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.ArrowDown) {
            moveSelection(1, viewState);
            return ClientInteractionResult.none();
        }
        if (keyType == KeyType.Enter) {
            return enterActivePane(viewState);
        }
        if (keyType == KeyType.Character) {
            return handleNavigationCharacter(key.getCharacter(), viewState, serverAddress);
        }
        return ClientInteractionResult.none();
    }

    private ClientInteractionResult handleNavigationCharacter(
            Character character,
            ClientViewState viewState,
            String serverAddress
    ) {
        if (character == null || Character.isISOControl(character)) {
            return ClientInteractionResult.none();
        }

        char value = character;
        char shortcut = Character.toLowerCase(value);
        if (value == ':') {
            beginInput(ClientInputMode.RAW_COMMAND);
            return ClientInteractionResult.status("Raw command mode.");
        }
        if (shortcut == 'r') {
            return ClientInteractionResult.commands(ClientCommandComposer.listRefreshCommands());
        }
        if (shortcut == 'h') {
            activePane = activePane.previous();
            return ClientInteractionResult.none();
        }
        if (shortcut == 'l') {
            if (!viewState.loggedIn()) {
                beginInput(ClientInputMode.LOGIN);
                return ClientInteractionResult.status("Login mode.");
            }
            activePane = activePane.next();
            return ClientInteractionResult.none();
        }
        if (shortcut == 'j') {
            moveSelection(1, viewState);
            return ClientInteractionResult.none();
        }
        if (shortcut == 'k') {
            moveSelection(-1, viewState);
            return ClientInteractionResult.none();
        }
        if (shortcut == 'a' && activePane == ClientPane.ROOMS) {
            beginInput(ClientInputMode.CREATE_ROOM);
            return ClientInteractionResult.status("Create room mode.");
        }
        if (shortcut == 'q' && activePane == ClientPane.ROOMS) {
            return leaveSelectedRoom(viewState);
        }
        if (shortcut == 'w' && activePane == ClientPane.USERS) {
            return beginWhisper(viewState);
        }
        if (activePane == ClientPane.CHAT) {
            return beginChatWithCharacter(value, viewState);
        }
        return ClientInteractionResult.none();
    }

    private ClientInteractionResult enterActivePane(ClientViewState viewState) {
        return switch (activePane) {
            case ROOMS -> joinSelectedRoom(viewState);
            case CHAT -> beginChat(viewState);
            case USERS -> beginWhisper(viewState);
            case RAW_LOG -> {
                beginInput(ClientInputMode.RAW_COMMAND);
                yield ClientInteractionResult.status("Raw command mode.");
            }
        };
    }

    private ClientInteractionResult submitInput(ClientViewState viewState, String serverAddress) {
        try {
            ClientCommand command = ClientCommandComposer.compose(
                    inputMode,
                    input.toString(),
                    viewState.currentRoom(),
                    whisperTarget,
                    serverAddress
            );
            cancelInput();
            return ClientInteractionResult.command(command);
        } catch (RuntimeException exception) {
            return ClientInteractionResult.status("Input error: " + exception.getMessage());
        }
    }

    private ClientInteractionResult joinSelectedRoom(ClientViewState viewState) {
        String room = selectedRoom(viewState);
        if (room.isBlank()) {
            return ClientInteractionResult.status("No room selected.");
        }
        try {
            return ClientInteractionResult.command(ClientCommandComposer.joinRoom(room));
        } catch (RuntimeException exception) {
            return ClientInteractionResult.status("Input error: " + exception.getMessage());
        }
    }

    private ClientInteractionResult leaveSelectedRoom(ClientViewState viewState) {
        String room = selectedRoom(viewState);
        if (room.isBlank()) {
            room = viewState.currentRoom();
        }
        if (room.isBlank()) {
            return ClientInteractionResult.status("No room selected.");
        }
        try {
            return ClientInteractionResult.command(ClientCommandComposer.leaveRoom(room));
        } catch (RuntimeException exception) {
            return ClientInteractionResult.status("Input error: " + exception.getMessage());
        }
    }

    private ClientInteractionResult beginChat(ClientViewState viewState) {
        if (viewState.currentRoom().isBlank()) {
            return ClientInteractionResult.status("Join a room before chatting.");
        }
        beginInput(ClientInputMode.CHAT_MESSAGE);
        return ClientInteractionResult.status("Chat message mode.");
    }

    private ClientInteractionResult beginChatWithCharacter(
            char character,
            ClientViewState viewState
    ) {
        ClientInteractionResult result = beginChat(viewState);
        if (result.hasStatus() && !inputActive()) {
            return result;
        }
        ClientInteractionResult appendResult = appendCharacter(new KeyStroke(character, false, false));
        return appendResult.hasStatus() ? appendResult : ClientInteractionResult.none();
    }

    private ClientInteractionResult beginWhisper(ClientViewState viewState) {
        String user = selectedUser(viewState);
        if (user.isBlank()) {
            return ClientInteractionResult.status("No user selected.");
        }
        whisperTarget = user;
        beginInput(ClientInputMode.WHISPER);
        return ClientInteractionResult.status("Whisper mode for " + user + ".");
    }

    private void beginInput(ClientInputMode mode) {
        inputMode = Objects.requireNonNull(mode, "mode");
        input.setLength(0);
        if (mode != ClientInputMode.WHISPER) {
            whisperTarget = "";
        }
    }

    private void cancelInput() {
        inputMode = null;
        input.setLength(0);
        whisperTarget = "";
    }

    private ClientInteractionResult appendCharacter(KeyStroke key) {
        Character character = key.getCharacter();
        if (character == null || Character.isISOControl(character)) {
            return ClientInteractionResult.none();
        }
        if (input.codePointCount(0, input.length()) >= MAX_INPUT_CODE_POINTS) {
            return ClientInteractionResult.status("Input limit is " + MAX_INPUT_CODE_POINTS + " characters.");
        }
        input.append(character);
        return ClientInteractionResult.none();
    }

    private void backspaceInput() {
        if (input.isEmpty()) {
            return;
        }
        int previous = input.offsetByCodePoints(input.length(), -1);
        input.delete(previous, input.length());
    }

    private void moveSelection(int delta, ClientViewState viewState) {
        if (activePane == ClientPane.ROOMS) {
            selectedRoomIndex = clamp(selectedRoomIndex + delta, viewState.rooms().size());
        } else if (activePane == ClientPane.USERS) {
            selectedUserIndex = clamp(selectedUserIndex + delta, viewState.users().size());
        }
    }

    private String selectedRoom(ClientViewState viewState) {
        List<String> rooms = viewState.rooms();
        if (rooms.isEmpty()) {
            return "";
        }
        return rooms.get(selectedRoomIndex);
    }

    private String selectedUser(ClientViewState viewState) {
        List<String> users = viewState.users();
        if (users.isEmpty()) {
            return "";
        }
        return users.get(selectedUserIndex);
    }

    private String roomLabel(ClientViewState viewState) {
        String currentRoom = viewState.currentRoom();
        return currentRoom.isBlank() ? "room" : currentRoom;
    }

    private static int clamp(int value, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(value, size - 1));
    }
}
