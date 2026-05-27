package dev.eatsteak.jchatroom.common.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProtocolParser {
    private ProtocolParser() {
    }

    public static ProtocolLine parseLine(String line) {
        ProtocolValidator.requireLine(line);

        List<String> tokens = new ArrayList<>();
        Optional<String> trailingText = Optional.empty();
        int index = 0;

        while (index < line.length()) {
            while (index < line.length() && line.charAt(index) == ' ') {
                index++;
            }
            if (index >= line.length()) {
                break;
            }

            if (line.charAt(index) == ':') {
                trailingText = Optional.of(line.substring(index + 1));
                break;
            }

            int tokenStart = index;
            while (index < line.length() && line.charAt(index) != ' ') {
                index++;
            }
            tokens.add(line.substring(tokenStart, index));
        }

        return new ProtocolLine(tokens, trailingText);
    }

    public static ClientRequest parseRequest(String line) {
        ProtocolLine parsed = parseLine(line);

        return switch (parsed.command()) {
            case "LOGIN" -> parseLogin(parsed);
            case "ROOM" -> parseRoom(parsed);
            case "MSG" -> parseMessage(parsed);
            case "WHISPER" -> parseWhisper(parsed);
            case "LIST" -> parseList(parsed);
            case "QUIT" -> parseQuit(parsed);
            default -> throw ProtocolValidator.malformed("unknown command");
        };
    }

    private static ClientRequest.Login parseLogin(ProtocolLine line) {
        requireTokenCount(line, 2);
        requireNoTrailingText(line);
        return new ClientRequest.Login(line.tokens().get(1));
    }

    private static ClientRequest.Room parseRoom(ProtocolLine line) {
        requireTokenCount(line, 3);
        requireNoTrailingText(line);
        return new ClientRequest.Room(RoomAction.fromToken(line.tokens().get(1)), line.tokens().get(2));
    }

    private static ClientRequest.Message parseMessage(ProtocolLine line) {
        requireTokenCount(line, 2);
        String message = requireTrailingText(line);
        return new ClientRequest.Message(line.tokens().get(1), message);
    }

    private static ClientRequest.Whisper parseWhisper(ProtocolLine line) {
        requireTokenCount(line, 2);
        String message = requireTrailingText(line);
        return new ClientRequest.Whisper(line.tokens().get(1), message);
    }

    private static ClientRequest.ListQuery parseList(ProtocolLine line) {
        requireTokenCount(line, 2);
        requireNoTrailingText(line);
        return new ClientRequest.ListQuery(ListTarget.fromToken(line.tokens().get(1)));
    }

    private static ClientRequest.Quit parseQuit(ProtocolLine line) {
        requireTokenCount(line, 1);
        requireNoTrailingText(line);
        return new ClientRequest.Quit();
    }

    private static void requireTokenCount(ProtocolLine line, int expected) {
        if (line.tokens().size() != expected) {
            throw ProtocolValidator.malformed("wrong number of arguments");
        }
    }

    private static String requireTrailingText(ProtocolLine line) {
        return line.trailingText()
                .orElseThrow(() -> ProtocolValidator.malformed("trailing text is required"));
    }

    private static void requireNoTrailingText(ProtocolLine line) {
        if (line.trailingText().isPresent()) {
            throw ProtocolValidator.malformed("unexpected trailing text");
        }
    }
}
