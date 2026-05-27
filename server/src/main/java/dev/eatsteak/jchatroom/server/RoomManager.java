package dev.eatsteak.jchatroom.server;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class RoomManager {
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    synchronized CreateResult create(String roomName, Session creator) {
        Objects.requireNonNull(roomName, "roomName");
        Objects.requireNonNull(creator, "creator");

        if (!creator.isActive()) {
            return CreateResult.inactiveSession();
        }

        AtomicBoolean created = new AtomicBoolean();
        Room room = rooms.computeIfAbsent(roomName, name -> {
            created.set(true);
            return new Room(name);
        });
        if (!created.get()) {
            return CreateResult.alreadyExists();
        }

        if (!room.add(creator) || !creator.isActive()) {
            room.remove(creator);
            rooms.remove(roomName, room);
            return CreateResult.inactiveSession();
        }
        return CreateResult.created(room.membersSnapshot());
    }

    synchronized JoinResult join(String roomName, Session session) {
        Objects.requireNonNull(roomName, "roomName");
        Objects.requireNonNull(session, "session");

        if (!session.isActive()) {
            return JoinResult.inactiveSession();
        }

        Room room = rooms.get(roomName);
        if (room == null) {
            return JoinResult.notFound();
        }

        boolean joined = room.add(session);
        if (!session.isActive()) {
            room.remove(session);
            if (room.isEmpty()) {
                rooms.remove(roomName, room);
            }
            return JoinResult.inactiveSession();
        }
        return JoinResult.joined(room.membersSnapshot(), joined);
    }

    synchronized LeaveResult leave(String roomName, Session session) {
        Objects.requireNonNull(roomName, "roomName");
        Objects.requireNonNull(session, "session");

        if (!session.isActive()) {
            return LeaveResult.inactiveSession();
        }

        Room room = rooms.get(roomName);
        if (room == null) {
            return LeaveResult.notFound();
        }
        if (!room.remove(session)) {
            return LeaveResult.notMember();
        }

        List<Session> remainingMembers = room.membersSnapshot();
        if (room.isEmpty()) {
            rooms.remove(roomName, room);
        }
        return LeaveResult.left(remainingMembers);
    }

    synchronized MessageTargets messageTargets(String roomName, Session sender) {
        Objects.requireNonNull(roomName, "roomName");
        Objects.requireNonNull(sender, "sender");

        if (!sender.isActive()) {
            return MessageTargets.inactiveSession();
        }

        Room room = rooms.get(roomName);
        if (room == null) {
            return MessageTargets.notFound();
        }
        if (!room.contains(sender)) {
            return MessageTargets.notMember();
        }
        return MessageTargets.found(room.membersSnapshot());
    }

    synchronized void removeFromAll(Session session) {
        Objects.requireNonNull(session, "session");

        for (Room room : rooms.values()) {
            if (room.remove(session) && room.isEmpty()) {
                rooms.remove(room.name(), room);
            }
        }
    }

    synchronized List<String> roomNames() {
        for (Room room : rooms.values()) {
            if (!room.hasActiveMembers()) {
                rooms.remove(room.name(), room);
            }
        }
        return rooms.values().stream()
                .map(Room::name)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    synchronized void clear() {
        rooms.clear();
    }

    enum CreateStatus {
        CREATED,
        ALREADY_EXISTS,
        INACTIVE_SESSION
    }

    record CreateResult(CreateStatus status, List<Session> members) {
        private static CreateResult created(List<Session> members) {
            return new CreateResult(CreateStatus.CREATED, List.copyOf(members));
        }

        private static CreateResult alreadyExists() {
            return new CreateResult(CreateStatus.ALREADY_EXISTS, List.of());
        }

        private static CreateResult inactiveSession() {
            return new CreateResult(CreateStatus.INACTIVE_SESSION, List.of());
        }
    }

    enum JoinStatus {
        JOINED,
        ALREADY_MEMBER,
        NOT_FOUND,
        INACTIVE_SESSION
    }

    record JoinResult(JoinStatus status, List<Session> members) {
        private static JoinResult joined(List<Session> members, boolean joined) {
            return new JoinResult(joined ? JoinStatus.JOINED : JoinStatus.ALREADY_MEMBER, List.copyOf(members));
        }

        private static JoinResult notFound() {
            return new JoinResult(JoinStatus.NOT_FOUND, List.of());
        }

        private static JoinResult inactiveSession() {
            return new JoinResult(JoinStatus.INACTIVE_SESSION, List.of());
        }
    }

    enum LeaveStatus {
        LEFT,
        NOT_MEMBER,
        NOT_FOUND,
        INACTIVE_SESSION
    }

    record LeaveResult(LeaveStatus status, List<Session> remainingMembers) {
        private static LeaveResult left(List<Session> remainingMembers) {
            return new LeaveResult(LeaveStatus.LEFT, List.copyOf(remainingMembers));
        }

        private static LeaveResult notMember() {
            return new LeaveResult(LeaveStatus.NOT_MEMBER, List.of());
        }

        private static LeaveResult notFound() {
            return new LeaveResult(LeaveStatus.NOT_FOUND, List.of());
        }

        private static LeaveResult inactiveSession() {
            return new LeaveResult(LeaveStatus.INACTIVE_SESSION, List.of());
        }
    }

    enum MessageTargetStatus {
        FOUND,
        NOT_MEMBER,
        NOT_FOUND,
        INACTIVE_SESSION
    }

    record MessageTargets(MessageTargetStatus status, List<Session> members) {
        private static MessageTargets found(List<Session> members) {
            return new MessageTargets(MessageTargetStatus.FOUND, List.copyOf(members));
        }

        private static MessageTargets notMember() {
            return new MessageTargets(MessageTargetStatus.NOT_MEMBER, List.of());
        }

        private static MessageTargets notFound() {
            return new MessageTargets(MessageTargetStatus.NOT_FOUND, List.of());
        }

        private static MessageTargets inactiveSession() {
            return new MessageTargets(MessageTargetStatus.INACTIVE_SESSION, List.of());
        }
    }
}
