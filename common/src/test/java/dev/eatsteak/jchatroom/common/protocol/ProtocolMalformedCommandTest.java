package dev.eatsteak.jchatroom.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolMalformedCommandTest {
    @Test
    void rejectsMissingLoginArgument() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("LOGIN")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
        assertEquals(400, exception.numericCode());
    }

    @Test
    void rejectsUnknownRoomAction() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("ROOM RENAME lobby")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }

    @Test
    void rejectsMessageWithoutTrailingText() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("MSG lobby hello")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }

    @Test
    void rejectsUnexpectedTrailingText() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("LIST USERS :extra")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }

    @Test
    void rejectsUnknownCommand() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("PING")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }
}
