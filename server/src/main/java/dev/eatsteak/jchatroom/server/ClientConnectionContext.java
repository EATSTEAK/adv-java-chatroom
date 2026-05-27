package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;

interface ClientConnectionContext {
    void sendLine(String line);

    void closeAfterWrites();

    void closeWithError(ProtocolErrorCode errorCode, String message);

    void closeNow();

    default boolean isOpen() {
        return true;
    }

    default String remoteAddress() {
        return "unknown";
    }

    default void logAccess(String event, Object... details) {
    }

    default void logAccessError(String event, Throwable failure, Object... details) {
    }
}
