package io.github.aliensockpuppet.dorkcore.api;

import java.util.UUID;

public interface Cooldowns {
    long remaining(UUID player, String key);

    boolean ready(UUID player, String key);

    void start(UUID player, String key, long millis);

    void clear(UUID player, String key);

    long roll(String key, long defaultSeconds);
}
