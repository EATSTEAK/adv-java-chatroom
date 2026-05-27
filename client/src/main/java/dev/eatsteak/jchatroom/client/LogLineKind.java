package dev.eatsteak.jchatroom.client;

enum LogLineKind {
    OK("OK"),
    ERR("ERR"),
    EVENT("EVENT"),
    SENT("SENT"),
    SYSTEM("SYS"),
    SERVER("SERVER");

    private final String label;

    LogLineKind(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
