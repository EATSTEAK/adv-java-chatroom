package dev.eatsteak.jchatroom.common.protocol;

import java.util.Objects;

public sealed interface ClientRequest permits ClientRequest.Login, ClientRequest.Room, ClientRequest.Message,
        ClientRequest.Whisper, ClientRequest.ListQuery, ClientRequest.Quit {

    RequestType type();

    record Login(String username) implements ClientRequest {
        public Login {
            username = ProtocolValidator.requireUsername(username);
        }

        @Override
        public RequestType type() {
            return RequestType.LOGIN;
        }
    }

    record Room(RoomAction action, String room) implements ClientRequest {
        public Room {
            action = Objects.requireNonNull(action, "action");
            room = ProtocolValidator.requireRoom(room);
        }

        @Override
        public RequestType type() {
            return action.requestType();
        }
    }

    record Message(String room, String message) implements ClientRequest {
        public Message {
            room = ProtocolValidator.requireRoom(room);
            message = ProtocolValidator.requireMessageBody(message);
        }

        @Override
        public RequestType type() {
            return RequestType.MSG;
        }
    }

    record Whisper(String username, String message) implements ClientRequest {
        public Whisper {
            username = ProtocolValidator.requireUsername(username);
            message = ProtocolValidator.requireMessageBody(message);
        }

        @Override
        public RequestType type() {
            return RequestType.WHISPER;
        }
    }

    record ListQuery(ListTarget target) implements ClientRequest {
        public ListQuery {
            target = Objects.requireNonNull(target, "target");
        }

        @Override
        public RequestType type() {
            return target.requestType();
        }
    }

    record Quit() implements ClientRequest {
        @Override
        public RequestType type() {
            return RequestType.QUIT;
        }
    }
}
