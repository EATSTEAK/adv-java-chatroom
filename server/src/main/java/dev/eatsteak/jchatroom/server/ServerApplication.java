package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.ProjectInfo;

import java.io.IOException;

public final class ServerApplication {
    private ServerApplication() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ServerConfig config = ServerConfig.fromArgs(args);
        NioChatServer server = new NioChatServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "jchat-shutdown"));

        server.start();
        System.out.println(startupMessage(config, server.port()));
        server.awaitTermination();
    }

    static String startupMessage() {
        return ProjectInfo.name() + " server Stage 3 NIO connection state";
    }

    static String startupMessage(ServerConfig config, int boundPort) {
        return startupMessage()
                + " listening on port " + boundPort
                + " with " + config.reactorThreads() + " reactor threads";
    }
}
