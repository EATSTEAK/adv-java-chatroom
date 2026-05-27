package dev.eatsteak.jchatroom.server;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class Room {
    private final String name;
    private final Set<Session> members = ConcurrentHashMap.newKeySet();

    Room(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    String name() {
        return name;
    }

    boolean add(Session session) {
        Objects.requireNonNull(session, "session");
        if (!session.isActive()) {
            return false;
        }
        return members.add(session);
    }

    boolean remove(Session session) {
        return members.remove(Objects.requireNonNull(session, "session"));
    }

    boolean contains(Session session) {
        return members.contains(Objects.requireNonNull(session, "session"));
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    boolean hasActiveMembers() {
        return members.stream().anyMatch(Session::isActive);
    }

    List<Session> membersSnapshot() {
        return members.stream()
                .filter(Session::isActive)
                .sorted(Comparator.comparing(Session::username))
                .toList();
    }
}
