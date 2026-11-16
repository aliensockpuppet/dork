package io.github.aliensockpuppet.dorkcore.service;

import io.github.aliensockpuppet.dorkcore.api.Cooldowns;
import io.github.aliensockpuppet.dorkcore.internal.CooldownSettings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownsImpl implements Cooldowns {
    private final Map<UUID, Map<String, Long>> expiries = new ConcurrentHashMap<>();

    private final CooldownSettings settings;

    public CooldownsImpl(CooldownSettings settings) {
        this.settings = settings;
    }

    @Override
    public long remaining(UUID player, String key) {
        Map<String, Long> keys = expiries.get(player);
        if (keys == null) {
            return 0L;
        }
        Long expiry = keys.get(key);
        if (expiry == null) {
            return 0L;
        }
        long left = expiry - System.currentTimeMillis();
        if (left <= 0L) {
            keys.remove(key, expiry);
            return 0L;
        }
        return left;
    }

    @Override
    public boolean ready(UUID player, String key) {
        return remaining(player, key) == 0L;
    }

    @Override
    public void start(UUID player, String key, long millis) {
        if (millis <= 0L) {
            return;
        }
        long expiry = System.currentTimeMillis() + millis;
        expiries.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(key, expiry, Math::max);
    }

    @Override
    public long roll(String key, long defaultSeconds) {
        return settings.rollMillis(key, defaultSeconds);
    }

    @Override
    public void clear(UUID player, String key) {
        Map<String, Long> keys = expiries.get(player);
        if (keys != null) {
            keys.remove(key);
        }
    }

    public void forget(UUID player) {
        long now = System.currentTimeMillis();
        Map<String, Long> keys = expiries.get(player);
        if (keys != null) {
            keys.values().removeIf(expiry -> expiry <= now);
            expiries.remove(player, Map.of());
        }
        sweep(now);
    }

    private void sweep(long now) {
        expiries.values().forEach(keys -> keys.values().removeIf(expiry -> expiry <= now));
        expiries.values().removeIf(Map::isEmpty);
    }
}
