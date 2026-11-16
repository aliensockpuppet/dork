package io.github.aliensockpuppet.dorkstats.api;

import java.util.List;
import java.util.UUID;

public interface DorkStatsApi {
    long value(UUID player, StatType type);

    double kdr(UUID player);

    List<StatEntry> top(StatType type, int limit);

    List<KdrEntry> topKdr(int limit);

    void recordCombatLogKill(UUID killer, String killerName, UUID victim, String victimName);
}
