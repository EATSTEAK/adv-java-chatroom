package dev.eatsteak.jchatroom.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerApplicationTest {
    @Test
    void exposesStage2StartupMessage() {
        assertEquals("adv-java-chatroom server Stage 2 NIO lifecycle", ServerApplication.startupMessage());
    }

    @Test
    void formatsBoundStartupMessage() {
        ServerConfig config = new ServerConfig(5000, 2, 4, 16, 8, 600);

        assertEquals(
                "adv-java-chatroom server Stage 2 NIO lifecycle listening on port 5010 with 2 reactor threads",
                ServerApplication.startupMessage(config, 5010)
        );
    }
}
