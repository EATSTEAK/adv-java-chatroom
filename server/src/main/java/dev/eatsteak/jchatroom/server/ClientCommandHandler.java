package dev.eatsteak.jchatroom.server;

@FunctionalInterface
interface ClientCommandHandler {
    void handle(ClientConnectionContext connection, String line);
}
