package dev.eatsteak.jchatroom.common.protocol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface ServerMessage permits ServerMessage.Ok, ServerMessage.Error, ServerMessage.Event {
    record Ok(RequestType requestType, List<String> arguments) implements ServerMessage {
        public Ok {
            requestType = Objects.requireNonNull(requestType, "requestType");
            arguments = ProtocolValidator.copyAndValidateTokens("ok argument", arguments);
        }

        public Ok(RequestType requestType, String... arguments) {
            this(requestType, List.of(arguments));
        }
    }

    record Error(ProtocolErrorCode errorCode, String message) implements ServerMessage {
        public Error {
            errorCode = Objects.requireNonNull(errorCode, "errorCode");
            if (message == null || message.isBlank()) {
                message = errorCode.defaultMessage();
            }
            message = ProtocolValidator.requireTrailingText("error message", message);
        }
    }

    record Event(String type, List<String> arguments, Optional<String> trailingText) implements ServerMessage {
        public Event {
            type = ProtocolValidator.requireEventType(type);
            arguments = ProtocolValidator.copyAndValidateTokens("event argument", arguments);
            trailingText = Objects.requireNonNull(trailingText, "trailingText")
                    .map(text -> ProtocolValidator.requireTrailingText("event trailing text", text));
        }

        public Event(String type, List<String> arguments) {
            this(type, arguments, Optional.empty());
        }

        public Event(String type, List<String> arguments, String trailingText) {
            this(type, arguments, Optional.of(ProtocolValidator.requireTrailingText("event trailing text", trailingText)));
        }
    }

    static Ok ok(RequestType requestType, String... arguments) {
        return new Ok(requestType, arguments);
    }

    static Error error(ProtocolErrorCode errorCode, String message) {
        return new Error(errorCode, message);
    }

    static Event event(String type, List<String> arguments) {
        return new Event(type, arguments);
    }

    static Event event(String type, List<String> arguments, String trailingText) {
        return new Event(type, arguments, trailingText);
    }
}
