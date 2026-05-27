package dev.eatsteak.jchatroom.client;

import java.util.Objects;

record ClientCommand(String line, String successStatus) {
    ClientCommand {
        line = Objects.requireNonNull(line, "line");
        successStatus = Objects.requireNonNull(successStatus, "successStatus");
    }
}
