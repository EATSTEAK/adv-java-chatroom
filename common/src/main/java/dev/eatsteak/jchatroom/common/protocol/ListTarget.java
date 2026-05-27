package dev.eatsteak.jchatroom.common.protocol;

public enum ListTarget {
    USERS("USERS", RequestType.LIST_USERS),
    ROOMS("ROOMS", RequestType.LIST_ROOMS);

    private final String wireToken;
    private final RequestType requestType;

    ListTarget(String wireToken, RequestType requestType) {
        this.wireToken = wireToken;
        this.requestType = requestType;
    }

    public String wireToken() {
        return wireToken;
    }

    public RequestType requestType() {
        return requestType;
    }

    public static ListTarget fromToken(String token) {
        for (ListTarget target : values()) {
            if (target.wireToken.equals(token)) {
                return target;
            }
        }
        throw ProtocolValidator.malformed("unknown list target");
    }
}
