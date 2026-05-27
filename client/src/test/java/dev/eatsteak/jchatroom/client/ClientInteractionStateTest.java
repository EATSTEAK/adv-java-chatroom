package dev.eatsteak.jchatroom.client;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientInteractionStateTest {
    @Test
    void startsInLoginModeAndSubmitsLogin() {
        ClientInteractionState state = new ClientInteractionState();
        ClientViewState viewState = new ClientViewState();

        state.handleKey(character('a'), viewState, "host:1");
        state.handleKey(character('l'), viewState, "host:1");
        state.handleKey(character('i'), viewState, "host:1");
        state.handleKey(character('c'), viewState, "host:1");
        state.handleKey(character('e'), viewState, "host:1");
        ClientInteractionResult result = state.handleKey(key(KeyType.Enter), viewState, "host:1");

        assertEquals("LOGIN alice", result.commands().get(0).line());
        assertFalse(state.inputActive());
    }

    @Test
    void cyclesPanesWithTabAndHjkl() {
        ClientInteractionState state = loggedInState();
        ClientViewState viewState = loggedInViewState();

        state.handleKey(key(KeyType.Tab), viewState, "host:1");
        assertEquals(ClientPane.CHAT, state.activePane());

        state.handleKey(key(KeyType.Tab), viewState, "host:1");
        assertEquals(ClientPane.USERS, state.activePane());

        state.handleKey(character('h'), viewState, "host:1");
        assertEquals(ClientPane.CHAT, state.activePane());

        state.handleKey(character('l'), viewState, "host:1");
        assertEquals(ClientPane.USERS, state.activePane());
    }

    @Test
    void roomsPaneSelectsCreatesJoinsAndLeaves() {
        ClientInteractionState state = loggedInState();
        ClientViewState viewState = loggedInViewState();
        viewState.addServerLine(ServerLinePresentation.fromServerLine("OK LIST ROOMS lobby java"));

        state.handleKey(character('j'), viewState, "host:1");
        assertEquals(1, state.selectedRoomIndex());

        ClientInteractionResult joinResult = state.handleKey(key(KeyType.Enter), viewState, "host:1");
        assertEquals("ROOM JOIN java", joinResult.commands().get(0).line());

        ClientInteractionResult leaveResult = state.handleKey(character('q'), viewState, "host:1");
        assertEquals("ROOM LEAVE java", leaveResult.commands().get(0).line());

        state.handleKey(character('a'), viewState, "host:1");
        state.handleKey(character('n'), viewState, "host:1");
        state.handleKey(character('e'), viewState, "host:1");
        state.handleKey(character('w'), viewState, "host:1");
        ClientInteractionResult createResult = state.handleKey(key(KeyType.Enter), viewState, "host:1");
        assertEquals("ROOM CREATE new", createResult.commands().get(0).line());
    }

    @Test
    void usersPaneStartsWhisperToSelectedUser() {
        ClientInteractionState state = loggedInState();
        ClientViewState viewState = loggedInViewState();
        viewState.addServerLine(ServerLinePresentation.fromServerLine("OK LIST USERS alice bob"));

        state.handleKey(key(KeyType.Tab), viewState, "host:1");
        state.handleKey(key(KeyType.Tab), viewState, "host:1");
        state.handleKey(key(KeyType.ArrowDown), viewState, "host:1");
        state.handleKey(character('w'), viewState, "host:1");
        state.handleKey(character('h'), viewState, "host:1");
        state.handleKey(character('i'), viewState, "host:1");
        ClientInteractionResult result = state.handleKey(key(KeyType.Enter), viewState, "host:1");

        assertEquals("WHISPER bob :hi", result.commands().get(0).line());
    }

    @Test
    void chatPaneStartsMessageInput() {
        ClientInteractionState state = loggedInState();
        ClientViewState viewState = loggedInViewState();
        viewState.addServerLine(ServerLinePresentation.fromServerLine("OK ROOM JOIN lobby"));

        state.handleKey(key(KeyType.Tab), viewState, "host:1");
        state.handleKey(character('m'), viewState, "host:1");
        state.handleKey(character('s'), viewState, "host:1");
        state.handleKey(character('g'), viewState, "host:1");
        ClientInteractionResult result = state.handleKey(key(KeyType.Enter), viewState, "host:1");

        assertEquals("MSG lobby :msg", result.commands().get(0).line());
    }

    @Test
    void escapeCancelsInputBeforeRequestingExit() {
        ClientInteractionState state = loggedInState();
        ClientViewState viewState = loggedInViewState();

        state.handleKey(character(':'), viewState, "host:1");
        assertTrue(state.inputActive());

        ClientInteractionResult cancelResult = state.handleKey(key(KeyType.Escape), viewState, "host:1");
        assertFalse(cancelResult.exitRequested());
        assertFalse(state.inputActive());

        ClientInteractionResult exitResult = state.handleKey(key(KeyType.Escape), viewState, "host:1");
        assertTrue(exitResult.exitRequested());
    }

    private static ClientInteractionState loggedInState() {
        ClientInteractionState state = new ClientInteractionState();
        state.syncSelections(loggedInViewState());
        return state;
    }

    private static ClientViewState loggedInViewState() {
        ClientViewState viewState = new ClientViewState();
        viewState.addServerLine(ServerLinePresentation.fromServerLine("OK LOGIN alice"));
        return viewState;
    }

    private static KeyStroke key(KeyType keyType) {
        return new KeyStroke(keyType);
    }

    private static KeyStroke character(char character) {
        return new KeyStroke(character, false, false);
    }
}
