package dev.eatsteak.jchatroom.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolValidationTest {
    @Test
    void rejectsInvalidUsername() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("LOGIN not.valid")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }

    @Test
    void rejectsInvalidRoomName() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("ROOM JOIN room_name_that_is_too_long")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }

    @Test
    void rejectsOverlongLine() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("LOGIN " + "a".repeat(ProtocolValidator.MAX_LINE_LENGTH))
        );

        assertEquals(ProtocolErrorCode.TOO_LONG, exception.errorCode());
        assertEquals(413, exception.numericCode());
    }

    @Test
    void rejectsOverlongMessageBody() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> ProtocolParser.parseRequest("MSG lobby :" + "x".repeat(ProtocolValidator.MAX_MESSAGE_BODY_LENGTH + 1))
        );

        assertEquals(ProtocolErrorCode.TOO_LONG, exception.errorCode());
    }

    @Test
    void requestRecordsValidateDirectConstruction() {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> new ClientRequest.Whisper("bad user", "hello")
        );

        assertEquals(ProtocolErrorCode.MALFORMED_COMMAND, exception.errorCode());
    }
}
