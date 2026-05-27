package dev.eatsteak.jchatroom.server;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class Session {
    private final String username;
    private final ClientConnectionContext connection;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private volatile boolean active = true;

    Session(String username, ClientConnectionContext connection) {
        this.username = Objects.requireNonNull(username, "username");
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    String username() {
        return username;
    }

    ClientConnectionContext connection() {
        return connection;
    }

    boolean isActive() {
        return active && connection.isOpen();
    }

    boolean runIfActive(Runnable action) {
        Objects.requireNonNull(action, "action");

        lifecycle.readLock().lock();
        try {
            if (!isActive()) {
                return false;
            }
            action.run();
            return true;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    boolean sendLineIfActive(String line) {
        if (!lifecycle.readLock().tryLock()) {
            return false;
        }
        try {
            if (!isActive()) {
                return false;
            }
            connection.sendLine(line);
            return true;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    boolean deactivate() {
        lifecycle.writeLock().lock();
        try {
            if (!active) {
                return false;
            }
            active = false;
            return true;
        } finally {
            lifecycle.writeLock().unlock();
        }
    }
}
