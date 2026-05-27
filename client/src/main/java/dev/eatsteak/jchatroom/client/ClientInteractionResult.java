package dev.eatsteak.jchatroom.client;

import java.util.List;

record ClientInteractionResult(List<ClientCommand> commands, boolean exitRequested, String status) {
    ClientInteractionResult {
        commands = List.copyOf(commands);
        status = status == null ? "" : status;
    }

    static ClientInteractionResult none() {
        return new ClientInteractionResult(List.of(), false, "");
    }

    static ClientInteractionResult status(String status) {
        return new ClientInteractionResult(List.of(), false, status);
    }

    static ClientInteractionResult command(ClientCommand command) {
        return commands(List.of(command));
    }

    static ClientInteractionResult commands(List<ClientCommand> commands) {
        return new ClientInteractionResult(commands, false, "");
    }

    static ClientInteractionResult exit() {
        return new ClientInteractionResult(List.of(), true, "");
    }

    boolean hasStatus() {
        return !status.isBlank();
    }
}
