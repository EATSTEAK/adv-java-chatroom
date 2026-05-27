package dev.eatsteak.jchatroom.server;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class SessionManager {
    private final ConcurrentHashMap<String, Session> sessionsByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ClientConnectionContext, Session> sessionsByConnection = new ConcurrentHashMap<>();

    synchronized LoginResult login(ClientConnectionContext connection, String username) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(username, "username");

        if (!connection.isOpen()) {
            return LoginResult.connectionClosed();
        }
        if (sessionsByConnection.containsKey(connection)) {
            return LoginResult.alreadyLoggedIn();
        }

        Session session = new Session(username, connection);
        Session existingUsername = sessionsByUsername.putIfAbsent(username, session);
        if (existingUsername != null) {
            return LoginResult.duplicateUsername();
        }

        Session existingConnection = sessionsByConnection.putIfAbsent(connection, session);
        if (existingConnection != null) {
            sessionsByUsername.remove(username, session);
            return LoginResult.alreadyLoggedIn();
        }

        if (!connection.isOpen()) {
            removeConnectionLocked(connection);
            return LoginResult.connectionClosed();
        }

        return LoginResult.success(session);
    }

    synchronized Session sessionFor(ClientConnectionContext connection) {
        return sessionsByConnection.get(connection);
    }

    synchronized void remove(ClientConnectionContext connection) {
        removeConnectionLocked(connection);
    }

    synchronized void clear() {
        sessionsByConnection.clear();
        sessionsByUsername.clear();
    }

    synchronized int activeSessionCount() {
        return sessionsByUsername.size();
    }

    private void removeConnectionLocked(ClientConnectionContext connection) {
        Session session = sessionsByConnection.remove(connection);
        if (session != null) {
            sessionsByUsername.remove(session.username(), session);
        }
    }

    enum LoginStatus {
        SUCCESS,
        DUPLICATE_USERNAME,
        ALREADY_LOGGED_IN,
        CONNECTION_CLOSED
    }

    record LoginResult(LoginStatus status, Session session) {
        private static LoginResult success(Session session) {
            return new LoginResult(LoginStatus.SUCCESS, session);
        }

        private static LoginResult duplicateUsername() {
            return new LoginResult(LoginStatus.DUPLICATE_USERNAME, null);
        }

        private static LoginResult alreadyLoggedIn() {
            return new LoginResult(LoginStatus.ALREADY_LOGGED_IN, null);
        }

        private static LoginResult connectionClosed() {
            return new LoginResult(LoginStatus.CONNECTION_CLOSED, null);
        }
    }
}
