package dev.eatsteak.jchatroom.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NioChatServerIntegrationTest {
    @Test
    @Timeout(5)
    void acceptsReadsAndWritesPlaceholderEchoForMultipleClients() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            assertPlaceholderEcho(server.port(), "alpha");
            assertPlaceholderEcho(server.port(), "beta");
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void shutdownClosesAcceptLoopReactorsAndClientChannels() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        Socket socket = null;
        try {
            server.start();
            socket = connect(server.port());

            writeLine(socket, "before-shutdown");
            assertEquals("STAGE2:ECHO:before-shutdown", readLine(socket));
            assertTrue(server.isRunning());

            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
            assertFalse(server.isRunning());
            assertEquals(0, server.clientCount());
            assertEquals(-1, socket.getInputStream().read());
        } finally {
            if (socket != null) {
                socket.close();
            }
            server.close();
        }
    }

    @Test
    @Timeout(5)
    void reactorFailureCallbackCoordinatesFullServerShutdown() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        Socket socket = null;
        try {
            server.start();
            socket = connect(server.port());

            writeLine(socket, "before-reactor-failure");
            assertEquals("STAGE2:ECHO:before-reactor-failure", readLine(socket));
            assertTrue(server.isRunning());
            assertEquals(1, server.clientCount());

            server.reactorFailed(new IOException("selector failure"));

            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
            assertFalse(server.isRunning());
            assertEquals(0, server.clientCount());
            assertEquals(-1, socket.getInputStream().read());
        } finally {
            if (socket != null) {
                socket.close();
            }
            server.close();
        }
    }

    private static ServerConfig testConfig() {
        return new ServerConfig(0, 2, 2, 16, 8, 600);
    }

    private static void assertPlaceholderEcho(int port, String line) throws IOException {
        try (Socket socket = connect(port)) {
            writeLine(socket, line);
            assertEquals("STAGE2:ECHO:" + line, readLine(socket));
        }
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 1_000);
        socket.setSoTimeout(2_000);
        return socket;
    }

    private static void writeLine(Socket socket, String line) throws IOException {
        socket.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static String readLine(Socket socket) throws IOException {
        return new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        ).readLine();
    }
}
