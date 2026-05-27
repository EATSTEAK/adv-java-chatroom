package dev.eatsteak.jchatroom.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientConfigTest {
    @Test
    void parsesDefaults() {
        ClientConfig config = ClientConfig.fromArgs();

        assertEquals("127.0.0.1", config.host());
        assertEquals(5000, config.port());
    }

    @Test
    void parsesSpaceSeparatedOverrides() {
        ClientConfig config = ClientConfig.fromArgs("--host", "localhost", "--port", "6000");

        assertEquals("localhost", config.host());
        assertEquals(6000, config.port());
    }

    @Test
    void parsesEqualsSeparatedOverrides() {
        ClientConfig config = ClientConfig.fromArgs("--host=chat.internal", "--port=7000");

        assertEquals("chat.internal", config.host());
        assertEquals(7000, config.port());
    }

    @Test
    void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromArgs("--user", "alice"));
    }

    @Test
    void rejectsMissingAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromArgs("--host"));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromArgs("--host="));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromArgs("--port", "abc"));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromArgs("--port", "0"));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromArgs("--port", "65536"));
    }
}
