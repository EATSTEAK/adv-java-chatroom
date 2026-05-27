package dev.eatsteak.jchatroom.client;

enum ClientPane {
    ROOMS,
    CHAT,
    USERS,
    RAW_LOG;

    ClientPane next() {
        ClientPane[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    ClientPane previous() {
        ClientPane[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }
}
