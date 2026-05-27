package dev.eatsteak.jchatroom.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientCommandComposerTest {
    @Test
    void composesInputModeCommands() {
        assertEquals(
                "LOGIN alice",
                ClientCommandComposer.compose(ClientInputMode.LOGIN, "alice", "", "", "127.0.0.1:5000").line()
        );
        assertEquals(
                "ROOM CREATE lobby",
                ClientCommandComposer.compose(ClientInputMode.CREATE_ROOM, "lobby", "", "", "127.0.0.1:5000").line()
        );
        assertEquals(
                "MSG lobby :hello",
                ClientCommandComposer.compose(
                        ClientInputMode.CHAT_MESSAGE,
                        "hello",
                        "lobby",
                        "",
                        "127.0.0.1:5000"
                ).line()
        );
        assertEquals(
                "WHISPER bob :secret",
                ClientCommandComposer.compose(ClientInputMode.WHISPER, "secret", "", "bob", "127.0.0.1:5000").line()
        );
        assertEquals(
                "LIST USERS",
                ClientCommandComposer.compose(ClientInputMode.RAW_COMMAND, "LIST USERS", "", "", "host:1").line()
        );
    }

    @Test
    void composesImmediateCommands() {
        assertEquals("ROOM JOIN lobby", ClientCommandComposer.joinRoom("lobby").line());
        assertEquals("ROOM LEAVE lobby", ClientCommandComposer.leaveRoom("lobby").line());

        List<ClientCommand> commands = ClientCommandComposer.listRefreshCommands();
        assertEquals("LIST ROOMS", commands.get(0).line());
        assertEquals("LIST USERS", commands.get(1).line());
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(RuntimeException.class, () -> ClientCommandComposer.joinRoom("bad room"));
        assertThrows(
                RuntimeException.class,
                () -> ClientCommandComposer.compose(ClientInputMode.CHAT_MESSAGE, "", "lobby", "", "host:1")
        );
        assertThrows(
                RuntimeException.class,
                () -> ClientCommandComposer.compose(ClientInputMode.WHISPER, "hello", "", "bad user", "host:1")
        );
    }
}
