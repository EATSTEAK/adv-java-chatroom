package dev.eatsteak.jchatroom.client;

import dev.eatsteak.jchatroom.common.ProjectInfo;

public final class ClientApplication {
    private ClientApplication() {
    }

    public static void main(String[] args) {
        System.out.println(startupMessage());
    }

    static String startupMessage() {
        return ProjectInfo.name() + " client placeholder";
    }
}
