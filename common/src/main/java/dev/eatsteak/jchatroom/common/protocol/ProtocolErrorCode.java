package dev.eatsteak.jchatroom.common.protocol;

import java.util.Arrays;
import java.util.Optional;

public enum ProtocolErrorCode {
    MALFORMED_COMMAND(400, "malformed command"),
    LOGIN_REQUIRED(401, "login required"),
    NOT_FOUND(404, "user or room not found"),
    INVALID_STATE(409, "duplicate username or invalid state"),
    TOO_LONG(413, "line/message too long"),
    CAPACITY_EXCEEDED(429, "queue full or server full"),
    INTERNAL_ERROR(500, "internal server error");

    private final int code;
    private final String defaultMessage;

    ProtocolErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public static Optional<ProtocolErrorCode> fromCode(int code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.code == code)
                .findFirst();
    }
}
