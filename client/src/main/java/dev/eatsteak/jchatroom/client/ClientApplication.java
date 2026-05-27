package dev.eatsteak.jchatroom.client;

import dev.eatsteak.jchatroom.common.ProjectInfo;

import java.io.IOException;

public final class ClientApplication {
    static final String USAGE = "Usage: ./gradlew :client:run --args=\"[--host HOST] [--port PORT]\"";

    private ClientApplication() {
    }

    public static void main(String[] args) {
        ClientConfig config;
        try {
            config = ClientConfig.fromArgs(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(USAGE);
            System.err.println("Error: " + exception.getMessage());
            System.exit(2);
            return;
        }

        try {
            new TerminalChatClient(config).run();
        } catch (IOException exception) {
            System.err.println("Client failed: " + exception.getMessage());
            System.exit(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Client interrupted");
            System.exit(1);
        }
    }

    static String startupMessage(ClientConfig config) {
        return ProjectInfo.name() + " Lanterna client connecting to " + config.address();
    }
}
