package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.ProjectInfo;

public final class ServerApplication {
    private ServerApplication() {
    }

    public static void main(String[] args) {
        System.out.println(startupMessage());
    }

    static String startupMessage() {
        return ProjectInfo.name() + " server placeholder";
    }
}
