package dev.eatsteak.jchatroom.common.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProtocolFormatter {
    private ProtocolFormatter() {
    }

    public static String format(ServerMessage message) {
        if (message instanceof ServerMessage.Ok ok) {
            return formatOk(ok);
        }
        if (message instanceof ServerMessage.Error error) {
            return formatError(error);
        }
        if (message instanceof ServerMessage.Event event) {
            return formatEvent(event);
        }
        throw new IllegalArgumentException("Unsupported server message: " + message);
    }

    public static String ok(RequestType requestType, String... arguments) {
        return format(new ServerMessage.Ok(requestType, arguments));
    }

    public static String error(ProtocolErrorCode errorCode, String message) {
        return format(new ServerMessage.Error(errorCode, message));
    }

    public static String event(String type, List<String> arguments) {
        return format(new ServerMessage.Event(type, arguments));
    }

    public static String event(String type, List<String> arguments, String trailingText) {
        return format(new ServerMessage.Event(type, arguments, trailingText));
    }

    private static String formatOk(ServerMessage.Ok ok) {
        List<String> tokens = new ArrayList<>();
        tokens.add("OK");
        tokens.addAll(ok.requestType().wireTokens());
        tokens.addAll(ok.arguments());
        return formatLine(tokens, Optional.empty());
    }

    private static String formatError(ServerMessage.Error error) {
        return formatLine(List.of("ERR", Integer.toString(error.errorCode().code())), Optional.of(error.message()));
    }

    private static String formatEvent(ServerMessage.Event event) {
        List<String> tokens = new ArrayList<>();
        tokens.add("EVENT");
        tokens.add(event.type());
        tokens.addAll(event.arguments());
        return formatLine(tokens, event.trailingText());
    }

    private static String formatLine(List<String> tokens, Optional<String> trailingText) {
        if (tokens.isEmpty()) {
            throw ProtocolValidator.malformed("missing output token");
        }

        String tokenText = String.join(" ", ProtocolValidator.copyAndValidateTokens("output token", tokens));
        String line = trailingText
                .map(text -> tokenText + " :" + ProtocolValidator.requireTrailingText("trailing text", text))
                .orElse(tokenText);
        return ProtocolValidator.requireLine(line);
    }
}
