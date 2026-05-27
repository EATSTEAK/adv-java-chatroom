package dev.eatsteak.jchatroom.server;

@FunctionalInterface
interface ClientCommandHandler {
    void handle(ClientConnectionContext connection, String line);

    default void onConnectionClosed(ClientConnectionContext connection) {
    }

    default void shutdown() {
    }
}
