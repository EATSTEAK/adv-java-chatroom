package dev.eatsteak.jchatroom.common.protocol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProtocolLine(List<String> tokens, Optional<String> trailingText) {
    public ProtocolLine {
        tokens = ProtocolValidator.copyAndValidateTokens("line token", tokens);
        if (tokens.isEmpty()) {
            throw ProtocolValidator.malformed("missing command");
        }
        trailingText = Objects.requireNonNull(trailingText, "trailingText")
                .map(text -> ProtocolValidator.requireTrailingText("trailing text", text));
    }

    public String command() {
        return tokens.get(0);
    }

    public List<String> arguments() {
        if (tokens.size() == 1) {
            return List.of();
        }
        return List.copyOf(tokens.subList(1, tokens.size()));
    }
}
