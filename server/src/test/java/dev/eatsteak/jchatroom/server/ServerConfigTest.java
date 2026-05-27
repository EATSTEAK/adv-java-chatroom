package dev.eatsteak.jchatroom.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {
    @Test
    void parsesDefaults() {
        ServerConfig config = ServerConfig.fromArgs();

        assertEquals(5000, config.port());
        assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors()), config.reactorThreads());
        assertEquals(32, config.businessThreads());
        assertEquals(1024, config.maxClients());
        assertEquals(100, config.outboundQueueCapacity());
        assertEquals(600, config.idleTimeoutSeconds());
    }

    @Test
    void parsesSpaceSeparatedOverrides() {
        ServerConfig config = ServerConfig.fromArgs(
                "--port", "6000",
                "--reactor-threads", "2",
                "--business-threads", "4",
                "--max-clients", "64",
                "--queue-capacity", "12",
                "--idle-timeout-seconds", "30"
        );

        assertEquals(6000, config.port());
        assertEquals(2, config.reactorThreads());
        assertEquals(4, config.businessThreads());
        assertEquals(64, config.maxClients());
        assertEquals(12, config.outboundQueueCapacity());
        assertEquals(30, config.idleTimeoutSeconds());
    }

    @Test
    void parsesEqualsSeparatedOverrides() {
        ServerConfig config = ServerConfig.fromArgs(
                "--port=0",
                "--reactor-threads=1",
                "--business-threads=1",
                "--max-clients=2",
                "--queue-capacity=3",
                "--idle-timeout-seconds=4"
        );

        assertEquals(0, config.port());
        assertEquals(1, config.reactorThreads());
        assertEquals(1, config.businessThreads());
        assertEquals(2, config.maxClients());
        assertEquals(3, config.outboundQueueCapacity());
        assertEquals(4, config.idleTimeoutSeconds());
    }

    @Test
    void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromArgs("--unknown", "1"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromArgs("--port", "65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromArgs("--reactor-threads", "0"));
    }
}
