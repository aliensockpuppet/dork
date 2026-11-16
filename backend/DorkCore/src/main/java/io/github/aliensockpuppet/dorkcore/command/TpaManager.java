package io.github.aliensockpuppet.dorkcore.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaManager {
    public enum Direction { TO_TARGET, FROM_TARGET }

    public record Request(UUID sender, UUID target, Direction direction, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    private final Map<UUID, Request> byTarget = new ConcurrentHashMap<>();

    public Request open(UUID sender, UUID target, Direction direction, long ttlMillis) {
        Request request = new Request(sender, target, direction, System.currentTimeMillis() + ttlMillis);
        byTarget.put(target, request);
        return request;
    }

    public Request forTarget(UUID target) {
        Request request = byTarget.get(target);
        if (request == null) {
            return null;
        }
        if (request.expired()) {
            byTarget.remove(target, request);
            return null;
        }
        return request;
    }

    public Request fromSender(UUID sender) {
        for (Request request : byTarget.values()) {
            if (request.sender().equals(sender)) {
                return request.expired() ? null : request;
            }
        }
        return null;
    }

    public Request clearTarget(UUID target) {
        return byTarget.remove(target);
    }

    public void clearSender(UUID sender) {
        byTarget.values().removeIf(r -> r.sender().equals(sender));
    }

    public void forget(UUID player) {
        byTarget.remove(player);
        byTarget.values().removeIf(r -> r.sender().equals(player));
    }
}
