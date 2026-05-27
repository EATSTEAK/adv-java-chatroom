package dev.eatsteak.jchatroom.server;

import java.util.Objects;

record Session(String username, ClientConnectionContext connection) {
    Session {
        username = Objects.requireNonNull(username, "username");
        connection = Objects.requireNonNull(connection, "connection");
    }
}
