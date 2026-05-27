package dev.eatsteak.jchatroom.common.protocol;

import java.util.Objects;

public final class ProtocolException extends RuntimeException {
    private final ProtocolErrorCode errorCode;

    public ProtocolException(ProtocolErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode").defaultMessage());
    }

    public ProtocolException(ProtocolErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ProtocolErrorCode errorCode() {
        return errorCode;
    }

    public int numericCode() {
        return errorCode.code();
    }
}
