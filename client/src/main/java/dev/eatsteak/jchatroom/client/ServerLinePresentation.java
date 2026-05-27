package dev.eatsteak.jchatroom.client;

import dev.eatsteak.jchatroom.common.protocol.ProtocolLine;
import dev.eatsteak.jchatroom.common.protocol.ProtocolParser;

import java.util.Objects;

record ServerLinePresentation(
        LogLineKind kind,
        String text,
        boolean serverShutdown,
        String shutdownReason
) {
    private static final String SERVER_SHUTDOWN_EVENT = "SERVER_SHUTDOWN";

    ServerLinePresentation {
        kind = Objects.requireNonNull(kind, "kind");
        text = Objects.requireNonNull(text, "text");
        shutdownReason = shutdownReason == null ? "" : shutdownReason;
    }

    static ServerLinePresentation fromServerLine(String line) {
        Objects.requireNonNull(line, "line");

        ShutdownInfo shutdownInfo = shutdownInfo(line);
        return new ServerLinePresentation(
                classify(firstToken(line)),
                line,
                shutdownInfo.serverShutdown(),
                shutdownInfo.reason()
        );
    }

    static ServerLinePresentation sentCommand(String line) {
        return new ServerLinePresentation(LogLineKind.SENT, "-> " + line, false, "");
    }

    static ServerLinePresentation system(String message) {
        return new ServerLinePresentation(LogLineKind.SYSTEM, message, false, "");
    }

    String label() {
        return kind.label();
    }

    String shutdownStatus() {
        if (!serverShutdown) {
            return "";
        }
        if (shutdownReason.isBlank()) {
            return "Server shutdown";
        }
        return "Server shutdown: " + shutdownReason;
    }

    static String fit(String text, int width) {
        Objects.requireNonNull(text, "text");
        if (width <= 0) {
            return "";
        }
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= width) {
            return text;
        }
        if (width <= 3) {
            return text.substring(0, text.offsetByCodePoints(0, width));
        }
        int end = text.offsetByCodePoints(0, width - 3);
        return text.substring(0, end) + "...";
    }

    private static LogLineKind classify(String token) {
        return switch (token) {
            case "OK" -> LogLineKind.OK;
            case "ERR" -> LogLineKind.ERR;
            case "EVENT" -> LogLineKind.EVENT;
            default -> LogLineKind.SERVER;
        };
    }

    private static String firstToken(String line) {
        String trimmed = line.stripLeading();
        int separator = trimmed.indexOf(' ');
        if (separator < 0) {
            return trimmed;
        }
        return trimmed.substring(0, separator);
    }

    private static ShutdownInfo shutdownInfo(String line) {
        try {
            ProtocolLine parsed = ProtocolParser.parseLine(line);
            if (!"EVENT".equals(parsed.command())) {
                return ShutdownInfo.none();
            }
            if (parsed.arguments().isEmpty() || !SERVER_SHUTDOWN_EVENT.equals(parsed.arguments().get(0))) {
                return ShutdownInfo.none();
            }
            return new ShutdownInfo(true, parsed.trailingText().orElse(""));
        } catch (RuntimeException exception) {
            return ShutdownInfo.none();
        }
    }

    private record ShutdownInfo(boolean serverShutdown, String reason) {
        private static ShutdownInfo none() {
            return new ShutdownInfo(false, "");
        }
    }
}
