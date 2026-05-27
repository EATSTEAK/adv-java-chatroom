package dev.eatsteak.jchatroom.common.protocol;

import java.util.List;

public enum RequestType {
    LOGIN("LOGIN"),
    ROOM_CREATE("ROOM", "CREATE"),
    ROOM_JOIN("ROOM", "JOIN"),
    ROOM_LEAVE("ROOM", "LEAVE"),
    MSG("MSG"),
    WHISPER("WHISPER"),
    LIST_USERS("LIST", "USERS"),
    LIST_ROOMS("LIST", "ROOMS"),
    QUIT("QUIT");

    private final List<String> wireTokens;

    RequestType(String... wireTokens) {
        this.wireTokens = List.of(wireTokens);
    }

    public List<String> wireTokens() {
        return wireTokens;
    }

    public String wireName() {
        return String.join(" ", wireTokens);
    }
}
