package dev.eatsteak.jchatroom.server;

import dev.eatsteak.jchatroom.common.protocol.ClientRequest;
import dev.eatsteak.jchatroom.common.protocol.ProtocolErrorCode;
import dev.eatsteak.jchatroom.common.protocol.ProtocolException;
import dev.eatsteak.jchatroom.common.protocol.ProtocolFormatter;
import dev.eatsteak.jchatroom.common.protocol.ProtocolParser;

final class PlaceholderCommandHandler implements ClientCommandHandler {
    static final PlaceholderCommandHandler INSTANCE = new PlaceholderCommandHandler();

    private static final String TOO_LONG_MESSAGE = "line or message too long";

    private PlaceholderCommandHandler() {
    }

    @Override
    public void handle(ClientConnectionContext connection, String line) {
        try {
            ClientRequest request = ProtocolParser.parseRequest(line);
            connection.sendLine(ProtocolFormatter.ok(request.type()));
        } catch (ProtocolException exception) {
            if (exception.errorCode() == ProtocolErrorCode.TOO_LONG) {
                connection.closeWithError(ProtocolErrorCode.TOO_LONG, TOO_LONG_MESSAGE);
                return;
            }
            connection.sendLine(ProtocolFormatter.error(exception.errorCode(), exception.getMessage()));
        }
    }
}
