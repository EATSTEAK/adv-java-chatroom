package dev.eatsteak.jchatroom.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerApplicationTest {
    @Test
    void exposesPlaceholderStartupMessage() {
        assertEquals("adv-java-chatroom server placeholder", ServerApplication.startupMessage());
    }
}
