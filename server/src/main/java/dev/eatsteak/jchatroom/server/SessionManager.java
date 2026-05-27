package dev.eatsteak.jchatroom.server;

import java.util.Comparator;
import java.util.List;
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
            session.deactivate();
            return LoginResult.connectionClosed();
        }

        return LoginResult.success(session);
    }

    synchronized Session sessionFor(ClientConnectionContext connection) {
        Session session = sessionsByConnection.get(connection);
        if (session == null || !session.isActive()) {
            return null;
        }
        return session;
    }

    synchronized Session sessionForUsername(String username) {
        Session session = sessionsByUsername.get(username);
        if (session == null || !session.isActive()) {
            return null;
        }
        return session;
    }

    Session remove(ClientConnectionContext connection) {
        Objects.requireNonNull(connection, "connection");

        Session session;
        synchronized (this) {
            session = sessionsByConnection.get(connection);
        }
        if (session == null) {
            return null;
        }

        session.deactivate();

        synchronized (this) {
            if (!sessionsByConnection.remove(connection, session)) {
                return null;
            }
            sessionsByUsername.remove(session.username(), session);
        }
        return session;
    }

    void clear() {
        List<Session> sessions;
        synchronized (this) {
            sessions = List.copyOf(sessionsByConnection.values());
            sessionsByConnection.clear();
            sessionsByUsername.clear();
        }
        for (Session session : sessions) {
            session.deactivate();
        }
    }

    synchronized int activeSessionCount() {
        return (int) sessionsByUsername.values().stream()
                .filter(Session::isActive)
                .count();
    }

    synchronized List<String> usernames() {
        return sessionsByUsername.values().stream()
                .filter(Session::isActive)
                .map(Session::username)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    synchronized List<Session> activeSessions() {
        return sessionsByUsername.values().stream()
                .filter(Session::isActive)
                .sorted(Comparator.comparing(Session::username))
                .toList();
    }

    private Session removeConnectionLocked(ClientConnectionContext connection) {
        Session session = sessionsByConnection.remove(connection);
        if (session != null) {
            sessionsByUsername.remove(session.username(), session);
        }
        return session;
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
