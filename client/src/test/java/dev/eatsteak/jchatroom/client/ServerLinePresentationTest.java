package dev.eatsteak.jchatroom.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLinePresentationTest {
    @Test
    void classifiesServerOutputPrefixes() {
        assertEquals(LogLineKind.OK, ServerLinePresentation.fromServerLine("OK LOGIN alice").kind());
        assertEquals(LogLineKind.ERR, ServerLinePresentation.fromServerLine("ERR 404 :room not found").kind());
        assertEquals(LogLineKind.EVENT, ServerLinePresentation.fromServerLine("EVENT MSG lobby alice :hi").kind());
        assertEquals(LogLineKind.SERVER, ServerLinePresentation.fromServerLine("NOTICE maintenance").kind());
    }

    @Test
    void detectsServerShutdownEventWithReason() {
        ServerLinePresentation line = ServerLinePresentation.fromServerLine("EVENT SERVER_SHUTDOWN :maintenance");

        assertTrue(line.serverShutdown());
        assertEquals("maintenance", line.shutdownReason());
        assertEquals("Server shutdown: maintenance", line.shutdownStatus());
    }

    @Test
    void ignoresNonShutdownEvents() {
        ServerLinePresentation line = ServerLinePresentation.fromServerLine("EVENT ROOM_JOIN lobby alice");

        assertFalse(line.serverShutdown());
        assertEquals("", line.shutdownStatus());
    }

    @Test
    void createsLocalCommandPresentation() {
        ServerLinePresentation line = ServerLinePresentation.sentCommand("LIST USERS");

        assertEquals(LogLineKind.SENT, line.kind());
        assertEquals("SENT", line.label());
        assertEquals("-> LIST USERS", line.text());
    }

    @Test
    void fitsLongTextWithEllipsis() {
        assertEquals("abc", ServerLinePresentation.fit("abcdef", 3));
        assertEquals("ab...", ServerLinePresentation.fit("abcdef", 5));
        assertEquals("abcdef", ServerLinePresentation.fit("abcdef", 6));
        assertEquals("", ServerLinePresentation.fit("abcdef", 0));
    }
}
