package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ProtocolValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NioChatServerIntegrationTest {
    @Test
    @Timeout(5)
    void loginSuccessReturnsUsername() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                writeLine(socket, "LOGIN alpha");

                assertEquals("OK LOGIN alpha", reader(socket).readLine());
                assertEquals(1, server.activeSessionCount());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void duplicateUsernameIsRejectedUnderSeparateConnections() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket first = connect(server.port()); Socket second = connect(server.port())) {
                BufferedReader firstReader = reader(first);
                BufferedReader secondReader = reader(second);

                writeLine(first, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", firstReader.readLine());

                writeLine(second, "LOGIN alpha");
                assertEquals("ERR 409 :duplicate username", secondReader.readLine());
                assertEquals(1, server.activeSessionCount());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void loginBeforeCommandIsEnforcedWithoutClosingConnection() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);

                writeLine(socket, "LIST USERS");
                assertEquals("ERR 401 :login required", reader.readLine());

                writeLine(socket, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void quitRemovesSessionAndClosesConnectionAfterResponse() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);

                writeLine(socket, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader.readLine());

                writeLine(socket, "QUIT");
                assertEquals("OK QUIT", reader.readLine());
                assertNull(reader.readLine());
            }

            try (Socket socket = connect(server.port())) {
                writeLine(socket, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader(socket).readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void remoteSocketCloseRemovesSessionSoUsernameCanBeReused() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        Socket socket = null;
        try {
            server.start();
            socket = connect(server.port());
            BufferedReader reader = reader(socket);

            writeLine(socket, "LOGIN alpha");
            assertEquals("OK LOGIN alpha", reader.readLine());
            socket.close();
            socket = null;

            waitUntil(() -> server.activeSessionCount() == 0);

            try (Socket next = connect(server.port())) {
                writeLine(next, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader(next).readLine());
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void framesMultipleLinesFromOneRead() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeRaw(socket, "LOGIN alpha\nLIST USERS\n");

                assertEquals("OK LOGIN alpha", reader.readLine());
                assertEquals("ERR 409 :not implemented yet", reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void framesCommandSplitAcrossWrites() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                writeRaw(socket, "LOG");
                Thread.sleep(50);
                writeRaw(socket, "IN alpha\n");

                assertEquals("OK LOGIN alpha", reader(socket).readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void nonAsciiUtf8LineUnderMaxCodePointsIsAccepted() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeLine(socket, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader.readLine());
                writeLine(socket, "MSG lobby :" + "\uD55C".repeat(ProtocolValidator.MAX_MESSAGE_BODY_LENGTH));

                assertEquals("ERR 409 :not implemented yet", reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void lineOverMaxCodePointsReturnsErrorAndClosesConnection() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeLine(socket, "A".repeat(ProtocolValidator.MAX_LINE_LENGTH + 1));

                assertEquals("ERR 413 :line or message too long", reader.readLine());
                assertNull(reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void messageBodyLengthOverflowReturnsErrorAndClosesConnection() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeLine(socket, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader.readLine());
                writeLine(socket, "MSG lobby :" + "A".repeat(513));

                assertEquals("ERR 413 :line or message too long", reader.readLine());
                assertNull(reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void outboundQueueOverflowReturnsErrorAndClosesConnection() throws Exception {
        NioChatServer server = new NioChatServer(
                new ServerConfig(0, 1, 1, 16, 1, 600),
                (connection, line) -> {
                    connection.sendLine("OK LOGIN");
                    connection.sendLine("OK LIST USERS");
                }
        );
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeLine(socket, "LOGIN alpha");

                assertEquals("ERR 429 :outbound queue full", reader.readLine());
                assertNull(reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void inboundBacklogOverflowReturnsErrorAndClosesConnection() throws Exception {
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        CountDownLatch handlerFinished = new CountDownLatch(1);
        NioChatServer server = new NioChatServer(
                new ServerConfig(0, 1, 1, 16, 1, 600),
                (connection, line) -> {
                    handlerStarted.countDown();
                    try {
                        releaseHandler.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        handlerFinished.countDown();
                    }
                }
        );
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeRaw(socket, "LOGIN first\nLIST USERS\nQUIT\n");

                assertTrue(handlerStarted.await(1, TimeUnit.SECONDS));
                assertEquals("ERR 429 :inbound queue full", reader.readLine());
                assertNull(reader.readLine());
            }
        } finally {
            releaseHandler.countDown();
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
        assertTrue(handlerFinished.await(1, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(5)
    void idleTimeoutClosesInactiveConnectionAndRemovesSession() throws Exception {
        NioChatServer server = new NioChatServer(new ServerConfig(0, 1, 1, 16, 8, 1));
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeLine(socket, "LOGIN idle_user");
                assertEquals("OK LOGIN idle_user", reader.readLine());

                socket.setSoTimeout(4_000);
                assertNull(reader.readLine());
                assertEquals(0, server.clientCount());
                assertEquals(0, server.activeSessionCount());
            }

            try (Socket socket = connect(server.port())) {
                writeLine(socket, "LOGIN idle_user");
                assertEquals("OK LOGIN idle_user", reader(socket).readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void placeholderCommandsAfterLoginReturnNotImplementedWithoutClosing() throws Exception {
        NioChatServer server = new NioChatServer(testConfig());
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);

                writeLine(socket, "LOGIN alpha");
                assertEquals("OK LOGIN alpha", reader.readLine());

                writeLine(socket, "ROOM CREATE lobby");
                assertEquals("ERR 409 :not implemented yet", reader.readLine());

                writeLine(socket, "MSG lobby :hello");
                assertEquals("ERR 409 :not implemented yet", reader.readLine());

                writeLine(socket, "WHISPER beta :private");
                assertEquals("ERR 409 :not implemented yet", reader.readLine());

                writeLine(socket, "LIST USERS");
                assertEquals("ERR 409 :not implemented yet", reader.readLine());

                writeLine(socket, "QUIT");
                assertEquals("OK QUIT", reader.readLine());
            }
        } finally {
            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    @Timeout(5)
    void opWriteDrainsQueuedOutputWithoutHanging() throws Exception {
        NioChatServer server = new NioChatServer(
                new ServerConfig(0, 1, 1, 16, 64, 600),
                (connection, line) -> {
                    for (int i = 0; i < 20; i++) {
                        connection.sendLine("OK LOGIN " + i);
                    }
                }
        );
        try {
            server.start();

            try (Socket socket = connect(server.port())) {
                BufferedReader reader = reader(socket);
                writeLine(socket, "LOGIN alpha");

                for (int i = 0; i < 20; i++) {
                    assertEquals("OK LOGIN " + i, reader.readLine());
                }
            }
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
            BufferedReader reader = reader(socket);

            writeLine(socket, "LOGIN before_shutdown");
            assertEquals("OK LOGIN before_shutdown", reader.readLine());
            assertTrue(server.isRunning());
            assertEquals(1, server.activeSessionCount());

            server.shutdown();
            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
            assertFalse(server.isRunning());
            assertEquals(0, server.clientCount());
            assertEquals(0, server.activeSessionCount());
            assertNull(reader.readLine());
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
            BufferedReader reader = reader(socket);

            writeLine(socket, "LOGIN before_failure");
            assertEquals("OK LOGIN before_failure", reader.readLine());
            assertTrue(server.isRunning());
            assertEquals(1, server.clientCount());
            assertEquals(1, server.activeSessionCount());

            server.reactorFailed(new IOException("selector failure"));

            assertTrue(server.awaitTermination(Duration.ofSeconds(2)));
            assertFalse(server.isRunning());
            assertEquals(0, server.clientCount());
            assertEquals(0, server.activeSessionCount());
            assertNull(reader.readLine());
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

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 1_000);
        socket.setSoTimeout(2_000);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static void writeLine(Socket socket, String line) throws IOException {
        writeRaw(socket, line + "\n");
    }

    private static void writeRaw(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
    }
}
