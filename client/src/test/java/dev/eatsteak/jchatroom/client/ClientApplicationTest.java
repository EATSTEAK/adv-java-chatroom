package dev.eatsteak.jchatroom.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientApplicationTest {
    @Test
    void formatsStartupMessageForConfiguredAddress() {
        ClientConfig config = new ClientConfig("chat.example.test", 6000);

        assertEquals(
                "adv-java-chatroom Lanterna client connecting to chat.example.test:6000",
                ClientApplication.startupMessage(config)
        );
    }
}
