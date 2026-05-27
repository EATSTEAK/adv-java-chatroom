package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ClientRequest;
import dev.eatsteak.jchatroom.common.protocol.ListTarget;
import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;
import dev.eatsteak.jchatroom.common.protocol.ProtocolException;
import dev.eatsteak.jchatroom.common.protocol.ProtocolFormatter;
import dev.eatsteak.jchatroom.common.protocol.ProtocolLine;
import dev.eatsteak.jchatroom.common.protocol.ProtocolParser;
import dev.eatsteak.jchatroom.common.protocol.RequestType;
import dev.eatsteak.jchatroom.common.protocol.RoomAction;

import java.util.List;
import java.util.Objects;

final class SessionCommandHandler implements ClientCommandHandler {
    private static final String TOO_LONG_MESSAGE = "line or message too long";
    private static final String LOGIN_REQUIRED_MESSAGE = "login required";
    private static final String DUPLICATE_USERNAME_MESSAGE = "duplicate username";
    private static final String ALREADY_LOGGED_IN_MESSAGE = "already logged in";
    private static final String ROOM_NOT_FOUND_MESSAGE = "room not found";
    private static final String USER_NOT_FOUND_MESSAGE = "user not found";
    private static final String ROOM_ALREADY_EXISTS_MESSAGE = "room already exists";
    private static final String NOT_ROOM_MEMBER_MESSAGE = "not a room member";

    private final SessionManager sessions;
    private final RoomManager rooms;

    SessionCommandHandler() {
        this(new SessionManager(), new RoomManager());
    }

    SessionCommandHandler(SessionManager sessions) {
        this(sessions, new RoomManager());
    }

    SessionCommandHandler(SessionManager sessions, RoomManager rooms) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.rooms = Objects.requireNonNull(rooms, "rooms");
    }

    @Override
    public void handle(ClientConnectionContext connection, String line) {
        ProtocolLine protocolLine;
        try {
            protocolLine = ProtocolParser.parseLine(line);
        } catch (ProtocolException exception) {
            handleProtocolException(connection, line, exception);
            return;
        }

        Session session = sessions.sessionFor(connection);
        if (session == null && !isPreLoginCommand(protocolLine.command())) {
            connection.sendLine(ProtocolFormatter.error(ProtocolErrorCode.LOGIN_REQUIRED, LOGIN_REQUIRED_MESSAGE));
            return;
        }

        ClientRequest request;
        try {
            request = ProtocolParser.parseRequest(line);
        } catch (ProtocolException exception) {
            handleProtocolException(connection, line, exception);
            return;
        }

        if (request instanceof ClientRequest.Quit) {
            handleQuit(connection);
            return;
        }

        if (session == null) {
            if (request instanceof ClientRequest.Login login) {
                handleLogin(connection, login);
                return;
            }
            connection.sendLine(ProtocolFormatter.error(ProtocolErrorCode.LOGIN_REQUIRED, LOGIN_REQUIRED_MESSAGE));
            return;
        }

        if (request instanceof ClientRequest.Login) {
            connection.logAccess(
                    "LOGIN_FAILURE",
                    "reason", "already_logged_in",
                    "remote", connection.remoteAddress()
            );
            session.sendLineIfActive(
                    ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, ALREADY_LOGGED_IN_MESSAGE)
            );
            return;
        }

        session.runIfActive(() -> handleLoggedInRequest(session, request));
    }

    private void handleLoggedInRequest(Session session, ClientRequest request) {
        if (request instanceof ClientRequest.Room room) {
            handleRoom(session, room);
            return;
        }
        if (request instanceof ClientRequest.ListQuery listQuery) {
            handleList(session, listQuery);
            return;
        }
        if (request instanceof ClientRequest.Message message) {
            handleMessage(session, message);
            return;
        }
        if (request instanceof ClientRequest.Whisper whisper) {
            handleWhisper(session, whisper);
        }
    }

    @Override
    public void onConnectionClosed(ClientConnectionContext connection) {
        removeSession(connection);
    }

    @Override
    public void shutdown() {
        sessions.clear();
        rooms.clear();
    }

    int activeSessionCount() {
        return sessions.activeSessionCount();
    }

    private void handleLogin(ClientConnectionContext connection, ClientRequest.Login login) {
        SessionManager.LoginResult result = sessions.login(connection, login.username());
        switch (result.status()) {
            case SUCCESS -> {
                connection.logAccess(
                        "LOGIN_SUCCESS",
                        "username", login.username(),
                        "remote", connection.remoteAddress()
                );
                result.session().sendLineIfActive(ProtocolFormatter.ok(RequestType.LOGIN, login.username()));
            }
            case DUPLICATE_USERNAME -> {
                connection.logAccess(
                        "LOGIN_DUPLICATE",
                        "username", login.username(),
                        "remote", connection.remoteAddress()
                );
                connection.sendLine(ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, DUPLICATE_USERNAME_MESSAGE));
            }
            case ALREADY_LOGGED_IN -> {
                connection.logAccess(
                        "LOGIN_FAILURE",
                        "username", login.username(),
                        "reason", "already_logged_in",
                        "remote", connection.remoteAddress()
                );
                connection.sendLine(ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, ALREADY_LOGGED_IN_MESSAGE));
            }
            case CONNECTION_CLOSED -> {
                connection.logAccess(
                        "LOGIN_FAILURE",
                        "username", login.username(),
                        "reason", "connection_closed",
                        "remote", connection.remoteAddress()
                );
                // Close cleanup won the race; no response can be delivered.
            }
        }
    }

    private void handleQuit(ClientConnectionContext connection) {
        Session removed = removeSession(connection);
        connection.logAccess(
                "QUIT",
                "username", removed == null ? "" : removed.username(),
                "remote", connection.remoteAddress()
        );
        connection.sendLine(ProtocolFormatter.ok(RequestType.QUIT));
        connection.closeAfterWrites();
    }

    private void handleRoom(Session session, ClientRequest.Room room) {
        if (room.action() == RoomAction.CREATE) {
            handleRoomCreate(session, room.room());
            return;
        }
        if (room.action() == RoomAction.JOIN) {
            handleRoomJoin(session, room.room());
            return;
        }
        handleRoomLeave(session, room.room());
    }

    private void handleRoomCreate(Session session, String roomName) {
        RoomManager.CreateResult result = rooms.create(roomName, session);
        if (result.status() == RoomManager.CreateStatus.INACTIVE_SESSION) {
            return;
        }
        if (result.status() == RoomManager.CreateStatus.ALREADY_EXISTS) {
            session.connection().logAccess(
                    "ROOM_CREATE_FAILURE",
                    "username", session.username(),
                    "room", roomName,
                    "reason", "room_already_exists",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(
                    ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, ROOM_ALREADY_EXISTS_MESSAGE)
            );
            return;
        }

        session.connection().logAccess(
                "ROOM_CREATE",
                "username", session.username(),
                "room", roomName,
                "remote", session.connection().remoteAddress()
        );
        session.sendLineIfActive(ProtocolFormatter.ok(RequestType.ROOM_CREATE, roomName));
        sendEvent(result.members(), "ROOM_CREATE", List.of(roomName, session.username()));
    }

    private void handleRoomJoin(Session session, String roomName) {
        RoomManager.JoinResult result = rooms.join(roomName, session);
        if (result.status() == RoomManager.JoinStatus.INACTIVE_SESSION) {
            return;
        }
        if (result.status() == RoomManager.JoinStatus.NOT_FOUND) {
            session.connection().logAccess(
                    "ROOM_JOIN_FAILURE",
                    "username", session.username(),
                    "room", roomName,
                    "reason", "room_not_found",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(ProtocolFormatter.error(ProtocolErrorCode.NOT_FOUND, ROOM_NOT_FOUND_MESSAGE));
            return;
        }

        session.connection().logAccess(
                "ROOM_JOIN",
                "username", session.username(),
                "room", roomName,
                "status", result.status(),
                "remote", session.connection().remoteAddress()
        );
        session.sendLineIfActive(ProtocolFormatter.ok(RequestType.ROOM_JOIN, roomName));
        if (result.status() == RoomManager.JoinStatus.JOINED) {
            sendEvent(result.members(), "ROOM_JOIN", List.of(roomName, session.username()));
        }
    }

    private void handleRoomLeave(Session session, String roomName) {
        RoomManager.LeaveResult result = rooms.leave(roomName, session);
        if (result.status() == RoomManager.LeaveStatus.INACTIVE_SESSION) {
            return;
        }
        if (result.status() == RoomManager.LeaveStatus.NOT_FOUND) {
            session.connection().logAccess(
                    "ROOM_LEAVE_FAILURE",
                    "username", session.username(),
                    "room", roomName,
                    "reason", "room_not_found",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(ProtocolFormatter.error(ProtocolErrorCode.NOT_FOUND, ROOM_NOT_FOUND_MESSAGE));
            return;
        }
        if (result.status() == RoomManager.LeaveStatus.NOT_MEMBER) {
            session.connection().logAccess(
                    "ROOM_LEAVE_FAILURE",
                    "username", session.username(),
                    "room", roomName,
                    "reason", "not_room_member",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(
                    ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, NOT_ROOM_MEMBER_MESSAGE)
            );
            return;
        }

        session.connection().logAccess(
                "ROOM_LEAVE",
                "username", session.username(),
                "room", roomName,
                "remote", session.connection().remoteAddress()
        );
        session.sendLineIfActive(ProtocolFormatter.ok(RequestType.ROOM_LEAVE, roomName));
        sendEvent(result.remainingMembers(), "ROOM_LEAVE", List.of(roomName, session.username()));
    }

    private void handleList(Session session, ClientRequest.ListQuery listQuery) {
        List<String> values = listQuery.target() == ListTarget.USERS
                ? sessions.usernames()
                : rooms.roomNames();
        session.sendLineIfActive(ProtocolFormatter.ok(listQuery.type(), values.toArray(String[]::new)));
    }

    private void handleMessage(Session session, ClientRequest.Message message) {
        RoomManager.MessageTargets targets = rooms.messageTargets(message.room(), session);
        if (targets.status() == RoomManager.MessageTargetStatus.INACTIVE_SESSION) {
            return;
        }
        if (targets.status() == RoomManager.MessageTargetStatus.NOT_FOUND) {
            session.connection().logAccess(
                    "MSG_FAILURE",
                    "username", session.username(),
                    "room", message.room(),
                    "message", message.message(),
                    "reason", "room_not_found",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(ProtocolFormatter.error(ProtocolErrorCode.NOT_FOUND, ROOM_NOT_FOUND_MESSAGE));
            return;
        }
        if (targets.status() == RoomManager.MessageTargetStatus.NOT_MEMBER) {
            session.connection().logAccess(
                    "MSG_FAILURE",
                    "username", session.username(),
                    "room", message.room(),
                    "message", message.message(),
                    "reason", "not_room_member",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(
                    ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, NOT_ROOM_MEMBER_MESSAGE)
            );
            return;
        }

        session.connection().logAccess(
                "MSG",
                "username", session.username(),
                "room", message.room(),
                "message", message.message(),
                "recipients", targets.members().size(),
                "remote", session.connection().remoteAddress()
        );
        sendEvent(
                targets.members(),
                "MSG",
                List.of(message.room(), session.username()),
                message.message()
        );
    }

    private void handleWhisper(Session session, ClientRequest.Whisper whisper) {
        Session target = sessions.sessionForUsername(whisper.username());
        if (target == null) {
            session.connection().logAccess(
                    "WHISPER_FAILURE",
                    "from", session.username(),
                    "to", whisper.username(),
                    "message", whisper.message(),
                    "reason", "user_not_found",
                    "remote", session.connection().remoteAddress()
            );
            session.sendLineIfActive(ProtocolFormatter.error(ProtocolErrorCode.NOT_FOUND, USER_NOT_FOUND_MESSAGE));
            return;
        }

        session.connection().logAccess(
                "WHISPER",
                "from", session.username(),
                "to", whisper.username(),
                "message", whisper.message(),
                "remote", session.connection().remoteAddress()
        );
        session.sendLineIfActive(ProtocolFormatter.ok(RequestType.WHISPER, whisper.username()));
        target.sendLineIfActive(ProtocolFormatter.event(
                "WHISPER",
                List.of(session.username()),
                whisper.message()
        ));
    }

    private void sendEvent(List<Session> targets, String eventType, List<String> arguments) {
        String event = ProtocolFormatter.event(eventType, arguments);
        for (Session target : targets) {
            target.sendLineIfActive(event);
        }
    }

    private void sendEvent(List<Session> targets, String eventType, List<String> arguments, String trailingText) {
        String event = ProtocolFormatter.event(eventType, arguments, trailingText);
        for (Session target : targets) {
            target.sendLineIfActive(event);
        }
    }

    private Session removeSession(ClientConnectionContext connection) {
        Session removed = sessions.remove(connection);
        if (removed != null) {
            rooms.removeFromAll(removed);
        }
        return removed;
    }

    private boolean isPreLoginCommand(String command) {
        return "LOGIN".equals(command) || "QUIT".equals(command);
    }

    private void handleProtocolException(ClientConnectionContext connection, String line, ProtocolException exception) {
        if (line.startsWith("LOGIN")) {
            connection.logAccess(
                    "LOGIN_FAILURE",
                    "reason", exception.errorCode(),
                    "line", line,
                    "remote", connection.remoteAddress()
            );
        }
        if (exception.errorCode() == ProtocolErrorCode.TOO_LONG) {
            connection.closeWithError(ProtocolErrorCode.TOO_LONG, TOO_LONG_MESSAGE);
            return;
        }
        connection.sendLine(ProtocolFormatter.error(exception.errorCode(), exception.getMessage()));
    }
}
