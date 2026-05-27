package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ClientRequest;
import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;
import dev.eatsteak.jchatroom.common.protocol.ProtocolException;
import dev.eatsteak.jchatroom.common.protocol.ProtocolFormatter;
import dev.eatsteak.jchatroom.common.protocol.ProtocolLine;
import dev.eatsteak.jchatroom.common.protocol.ProtocolParser;
import dev.eatsteak.jchatroom.common.protocol.RequestType;

import java.util.Objects;

final class SessionCommandHandler implements ClientCommandHandler {
    private static final String TOO_LONG_MESSAGE = "line or message too long";
    private static final String LOGIN_REQUIRED_MESSAGE = "login required";
    private static final String DUPLICATE_USERNAME_MESSAGE = "duplicate username";
    private static final String ALREADY_LOGGED_IN_MESSAGE = "already logged in";
    private static final String NOT_IMPLEMENTED_MESSAGE = "not implemented yet";

    private final SessionManager sessions;

    SessionCommandHandler() {
        this(new SessionManager());
    }

    SessionCommandHandler(SessionManager sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public void handle(ClientConnectionContext connection, String line) {
        ProtocolLine protocolLine;
        try {
            protocolLine = ProtocolParser.parseLine(line);
        } catch (ProtocolException exception) {
            handleProtocolException(connection, exception);
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
            handleProtocolException(connection, exception);
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
            connection.sendLine(ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, ALREADY_LOGGED_IN_MESSAGE));
            return;
        }

        connection.sendLine(ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, NOT_IMPLEMENTED_MESSAGE));
    }

    @Override
    public void onConnectionClosed(ClientConnectionContext connection) {
        sessions.remove(connection);
    }

    @Override
    public void shutdown() {
        sessions.clear();
    }

    int activeSessionCount() {
        return sessions.activeSessionCount();
    }

    private void handleLogin(ClientConnectionContext connection, ClientRequest.Login login) {
        SessionManager.LoginResult result = sessions.login(connection, login.username());
        switch (result.status()) {
            case SUCCESS -> connection.sendLine(ProtocolFormatter.ok(RequestType.LOGIN, login.username()));
            case DUPLICATE_USERNAME -> connection.sendLine(
                    ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, DUPLICATE_USERNAME_MESSAGE)
            );
            case ALREADY_LOGGED_IN -> connection.sendLine(
                    ProtocolFormatter.error(ProtocolErrorCode.INVALID_STATE, ALREADY_LOGGED_IN_MESSAGE)
            );
            case CONNECTION_CLOSED -> {
                // Close cleanup won the race; no response can be delivered.
            }
        }
    }

    private void handleQuit(ClientConnectionContext connection) {
        sessions.remove(connection);
        connection.sendLine(ProtocolFormatter.ok(RequestType.QUIT));
        connection.closeAfterWrites();
    }

    private boolean isPreLoginCommand(String command) {
        return "LOGIN".equals(command) || "QUIT".equals(command);
    }

    private void handleProtocolException(ClientConnectionContext connection, ProtocolException exception) {
        if (exception.errorCode() == ProtocolErrorCode.TOO_LONG) {
            connection.closeWithError(ProtocolErrorCode.TOO_LONG, TOO_LONG_MESSAGE);
            return;
        }
        connection.sendLine(ProtocolFormatter.error(exception.errorCode(), exception.getMessage()));
    }
}
