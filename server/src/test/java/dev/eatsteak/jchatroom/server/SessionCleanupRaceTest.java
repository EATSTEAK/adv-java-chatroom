package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCleanupRaceTest {
    @Test
    void cleanupDeactivatesSessionAndIsIdempotent() {
        SessionManager sessions = new SessionManager();
        RecordingConnection connection = new RecordingConnection();
        Session session = sessions.login(connection, "alpha").session();

        assertTrue(session.isActive());
        assertSame(session, sessions.remove(connection));

        assertFalse(session.isActive());
        assertNull(sessions.sessionFor(connection));
        assertEquals(0, sessions.activeSessionCount());
        assertNull(sessions.remove(connection));
    }

    @Test
    void staleSessionCannotCreateOrJoinRoomsAfterCleanup() {
        SessionManager sessions = new SessionManager();
        RoomManager rooms = new RoomManager();

        RecordingConnection ownerConnection = new RecordingConnection();
        Session owner = sessions.login(ownerConnection, "owner").session();
        assertEquals(RoomManager.CreateStatus.CREATED, rooms.create("lobby", owner).status());

        RecordingConnection staleConnection = new RecordingConnection();
        Session stale = sessions.login(staleConnection, "stale").session();
        assertSame(stale, sessions.remove(staleConnection));

        assertEquals(RoomManager.CreateStatus.INACTIVE_SESSION, rooms.create("ghost", stale).status());
        assertEquals(RoomManager.JoinStatus.INACTIVE_SESSION, rooms.join("lobby", stale).status());
        assertEquals(List.of("lobby"), rooms.roomNames());
        assertEquals(List.of(owner), rooms.messageTargets("lobby", owner).members());
    }

    @Test
    void inactiveRoomMembersAreExcludedBeforeRoomCleanupCompletes() {
        SessionManager sessions = new SessionManager();
        RoomManager rooms = new RoomManager();

        RecordingConnection alphaConnection = new RecordingConnection();
        RecordingConnection betaConnection = new RecordingConnection();
        Session alpha = sessions.login(alphaConnection, "alpha").session();
        Session beta = sessions.login(betaConnection, "beta").session();

        assertEquals(RoomManager.CreateStatus.CREATED, rooms.create("lobby", alpha).status());
        assertEquals(RoomManager.JoinStatus.JOINED, rooms.join("lobby", beta).status());
        assertSame(beta, sessions.remove(betaConnection));

        RoomManager.MessageTargets targets = rooms.messageTargets("lobby", alpha);

        assertEquals(RoomManager.MessageTargetStatus.FOUND, targets.status());
        assertEquals(List.of(alpha), targets.members());
    }

    @Test
    void inactiveOnlyRoomsAreHiddenBeforeRoomCleanupCompletes() {
        SessionManager sessions = new SessionManager();
        RoomManager rooms = new RoomManager();

        RecordingConnection connection = new RecordingConnection();
        Session session = sessions.login(connection, "alpha").session();

        assertEquals(RoomManager.CreateStatus.CREATED, rooms.create("lobby", session).status());
        assertSame(session, sessions.remove(connection));

        assertEquals(List.of(), rooms.roomNames());
    }

    private static final class RecordingConnection implements ClientConnectionContext {
        private boolean open = true;

        @Override
        public void sendLine(String line) {
        }

        @Override
        public void closeAfterWrites() {
            open = false;
        }

        @Override
        public void closeWithError(ProtocolErrorCode errorCode, String message) {
            open = false;
        }

        @Override
        public void closeNow() {
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
