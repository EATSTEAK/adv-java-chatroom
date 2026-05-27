package dev.eatsteak.jchatroom.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolFormatterTest {
    @Test
    void formatsOkResponse() {
        String line = ProtocolFormatter.format(new ServerMessage.Ok(RequestType.ROOM_JOIN, "lobby"));

        assertEquals("OK ROOM JOIN lobby", line);
    }

    @Test
    void formatsErrorResponse() {
        String line = ProtocolFormatter.format(new ServerMessage.Error(ProtocolErrorCode.NOT_FOUND, "room not found"));

        assertEquals("ERR 404 :room not found", line);
    }

    @Test
    void formatsEventWithoutTrailingText() {
        String line = ProtocolFormatter.format(new ServerMessage.Event("USER_JOINED", List.of("lobby", "alice")));

        assertEquals("EVENT USER_JOINED lobby alice", line);
    }

    @Test
    void formatsEventWithTrailingText() {
        String line = ProtocolFormatter.format(new ServerMessage.Event("MESSAGE", List.of("lobby", "alice"), "hello world"));

        assertEquals("EVENT MESSAGE lobby alice :hello world", line);
    }

    @Test
    void formatsDefaultErrorMessage() {
        String line = ProtocolFormatter.error(ProtocolErrorCode.INTERNAL_ERROR, null);

        assertEquals("ERR 500 :internal server error", line);
    }
}
