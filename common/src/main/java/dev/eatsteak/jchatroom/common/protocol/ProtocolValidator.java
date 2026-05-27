package dev.eatsteak.jchatroom.common.protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class ProtocolValidator {
    public static final int MAX_LINE_LENGTH = 1024;
    public static final int MAX_MESSAGE_BODY_LENGTH = 512;

    private static final Pattern USERNAME_OR_ROOM = Pattern.compile("^[A-Za-z0-9_-]{1,20}$");
    private static final Pattern PROTOCOL_TOKEN = Pattern.compile("^[^\\s:][^\\s]*$");
    private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Z0-9_]{1,32}$");

    private ProtocolValidator() {
    }

    public static String requireLine(String line) {
        if (line == null) {
            throw malformed("line is required");
        }
        if (characterLength(line) > MAX_LINE_LENGTH) {
            throw tooLong("line is too long");
        }
        if (containsLineBreak(line)) {
            throw malformed("line must not contain CR or LF characters");
        }
        return line;
    }

    public static String requireUsername(String username) {
        return requireIdentifier("username", username);
    }

    public static String requireRoom(String room) {
        return requireIdentifier("room", room);
    }

    public static String requireIdentifier(String label, String value) {
        if (value == null || !USERNAME_OR_ROOM.matcher(value).matches()) {
            throw malformed("invalid " + label);
        }
        return value;
    }

    public static String requireMessageBody(String messageBody) {
        if (messageBody == null) {
            throw malformed("message body is required");
        }
        if (containsLineBreak(messageBody)) {
            throw malformed("message body must not contain CR or LF characters");
        }
        if (characterLength(messageBody) > MAX_MESSAGE_BODY_LENGTH) {
            throw tooLong("message body is too long");
        }
        return messageBody;
    }

    public static String requireTrailingText(String label, String text) {
        if (text == null) {
            throw malformed(label + " is required");
        }
        if (containsLineBreak(text)) {
            throw malformed(label + " must not contain CR or LF characters");
        }
        return text;
    }

    public static String requireProtocolToken(String label, String token) {
        if (token == null || !PROTOCOL_TOKEN.matcher(token).matches()) {
            throw malformed("invalid " + label);
        }
        return token;
    }

    public static String requireEventType(String type) {
        if (type == null || !EVENT_TYPE.matcher(type).matches()) {
            throw malformed("invalid event type");
        }
        return type;
    }

    public static List<String> copyAndValidateTokens(String label, Collection<String> tokens) {
        if (tokens == null) {
            throw malformed(label + " collection is required");
        }

        List<String> copy = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            copy.add(requireProtocolToken(label, token));
        }
        return List.copyOf(copy);
    }

    static ProtocolException malformed(String message) {
        return new ProtocolException(ProtocolErrorCode.MALFORMED_COMMAND, message);
    }

    static ProtocolException tooLong(String message) {
        return new ProtocolException(ProtocolErrorCode.TOO_LONG, message);
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static int characterLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
