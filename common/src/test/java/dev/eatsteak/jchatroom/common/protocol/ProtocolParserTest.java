package dev.eatsteak.jchatroom.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolParserTest {
    @Test
    void parsesLoginRequest() {
        ClientRequest request = ProtocolParser.parseRequest("LOGIN alice_1");

        ClientRequest.Login login = assertInstanceOf(ClientRequest.Login.class, request);
        assertEquals(RequestType.LOGIN, login.type());
        assertEquals("alice_1", login.username());
    }

    @Test
    void parsesRoomRequests() {
        ClientRequest.Room create = assertInstanceOf(
                ClientRequest.Room.class,
                ProtocolParser.parseRequest("ROOM CREATE lobby")
        );
        ClientRequest.Room join = assertInstanceOf(
                ClientRequest.Room.class,
                ProtocolParser.parseRequest("ROOM JOIN lobby")
        );
        ClientRequest.Room leave = assertInstanceOf(
                ClientRequest.Room.class,
                ProtocolParser.parseRequest("ROOM LEAVE lobby")
        );

        assertEquals(RoomAction.CREATE, create.action());
        assertEquals(RequestType.ROOM_CREATE, create.type());
        assertEquals(RoomAction.JOIN, join.action());
        assertEquals(RequestType.ROOM_JOIN, join.type());
        assertEquals(RoomAction.LEAVE, leave.action());
        assertEquals(RequestType.ROOM_LEAVE, leave.type());
        assertEquals("lobby", create.room());
    }

    @Test
    void parsesTrailingTextForMessage() {
        ClientRequest request = ProtocolParser.parseRequest("MSG lobby :hello world : again");

        ClientRequest.Message message = assertInstanceOf(ClientRequest.Message.class, request);
        assertEquals(RequestType.MSG, message.type());
        assertEquals("lobby", message.room());
        assertEquals("hello world : again", message.message());
    }

    @Test
    void parsesTrailingTextForWhisper() {
        ClientRequest request = ProtocolParser.parseRequest("WHISPER bob :private hello");

        ClientRequest.Whisper whisper = assertInstanceOf(ClientRequest.Whisper.class, request);
        assertEquals(RequestType.WHISPER, whisper.type());
        assertEquals("bob", whisper.username());
        assertEquals("private hello", whisper.message());
    }

    @Test
    void parsesListAndQuitRequests() {
        ClientRequest.ListQuery users = assertInstanceOf(
                ClientRequest.ListQuery.class,
                ProtocolParser.parseRequest("LIST USERS")
        );
        ClientRequest.ListQuery rooms = assertInstanceOf(
                ClientRequest.ListQuery.class,
                ProtocolParser.parseRequest("LIST ROOMS")
        );
        ClientRequest.Quit quit = assertInstanceOf(ClientRequest.Quit.class, ProtocolParser.parseRequest("QUIT"));

        assertEquals(ListTarget.USERS, users.target());
        assertEquals(RequestType.LIST_USERS, users.type());
        assertEquals(ListTarget.ROOMS, rooms.target());
        assertEquals(RequestType.LIST_ROOMS, rooms.type());
        assertEquals(RequestType.QUIT, quit.type());
    }

    @Test
    void parseLineExposesCommandArgumentsAndTrailingText() {
        ProtocolLine line = ProtocolParser.parseLine("MSG room-1 :hello there");

        assertEquals("MSG", line.command());
        assertEquals("room-1", line.arguments().get(0));
        assertTrue(line.trailingText().isPresent());
        assertEquals("hello there", line.trailingText().orElseThrow());
    }
}
