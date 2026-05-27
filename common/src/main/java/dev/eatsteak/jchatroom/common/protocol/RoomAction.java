package dev.eatsteak.jchatroom.common.protocol;

public enum RoomAction {
    CREATE("CREATE", RequestType.ROOM_CREATE),
    JOIN("JOIN", RequestType.ROOM_JOIN),
    LEAVE("LEAVE", RequestType.ROOM_LEAVE);

    private final String wireToken;
    private final RequestType requestType;

    RoomAction(String wireToken, RequestType requestType) {
        this.wireToken = wireToken;
        this.requestType = requestType;
    }

    public String wireToken() {
        return wireToken;
    }

    public RequestType requestType() {
        return requestType;
    }

    public static RoomAction fromToken(String token) {
        for (RoomAction action : values()) {
            if (action.wireToken.equals(token)) {
                return action;
            }
        }
        throw ProtocolValidator.malformed("unknown room action");
    }
}
