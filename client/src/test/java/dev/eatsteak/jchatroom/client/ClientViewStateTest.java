package dev.eatsteak.jchatroom.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientViewStateTest {
    @Test
    void replacesRoomsFromListResponse() {
        ClientViewState state = new ClientViewState();

        state.addServerLine(ServerLinePresentation.fromServerLine("OK LIST ROOMS lobby java"));

        assertEquals(List.of("lobby", "java"), state.rooms());
    }

    @Test
    void replacesUsersFromListResponse() {
        ClientViewState state = new ClientViewState();

        state.addServerLine(ServerLinePresentation.fromServerLine("OK LIST USERS alice bob"));

        assertEquals(List.of("alice", "bob"), state.users());
    }

    @Test
    void marksLoginSuccess() {
        ClientViewState state = new ClientViewState();

        state.addServerLine(ServerLinePresentation.fromServerLine("OK LOGIN alice"));

        assertTrue(state.loggedIn());
        assertEquals("alice", state.username());
    }

    @Test
    void appendsMessageEventToChatAndRawLog() {
        ClientViewState state = new ClientViewState();

        state.addServerLine(ServerLinePresentation.fromServerLine("EVENT MSG lobby alice :hello"));

        assertEquals("[lobby] alice: hello", state.chatLines().get(0).text());
        assertEquals("EVENT MSG lobby alice :hello", state.rawLines().get(0).text());
    }

    @Test
    void appendsWhisperEventToChat() {
        ClientViewState state = new ClientViewState();

        state.addServerLine(ServerLinePresentation.fromServerLine("EVENT WHISPER bob :secret"));

        assertEquals("[whisper] bob: secret", state.chatLines().get(0).text());
    }

    @Test
    void updatesCurrentRoomFromJoinAndLeaveResponses() {
        ClientViewState state = new ClientViewState();

        state.addServerLine(ServerLinePresentation.fromServerLine("OK ROOM JOIN lobby"));
        assertEquals("lobby", state.currentRoom());

        state.addServerLine(ServerLinePresentation.fromServerLine("OK ROOM LEAVE lobby"));
        assertEquals("", state.currentRoom());
    }

    @Test
    void recordsSentCommandsInRawLog() {
        ClientViewState state = new ClientViewState();

        state.addSentCommand("LIST USERS");

        assertEquals("-> LIST USERS", state.rawLines().get(0).text());
    }
}
