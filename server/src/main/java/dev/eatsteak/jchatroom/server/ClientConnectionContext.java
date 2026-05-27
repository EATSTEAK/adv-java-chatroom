package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;

interface ClientConnectionContext {
    void sendLine(String line);

    void closeAfterWrites();

    void closeWithError(ProtocolErrorCode errorCode, String message);

    void closeNow();
}
