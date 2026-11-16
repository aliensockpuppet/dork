package io.github.aliensockpuppet.dorkstats;

import io.github.aliensockpuppet.dorkstats.api.DorkStatsApi;
import io.github.aliensockpuppet.dorkstats.api.StatEntry;
import io.github.aliensockpuppet.dorkstats.api.StatType;
import io.github.aliensockpuppet.dorkstats.data.StatStore;

import java.util.List;
import java.util.UUID;

final class ApiImpl implements DorkStatsApi {
    private final StatStore store;

    ApiImpl(StatStore store) {
        this.store = store;
    }

    @Override
    public long value(UUID player, StatType type) {
        return store.value(player, type);
    }

    @Override
    public double kdr(UUID player) {
        return store.kdr(player);
    }

    @Override
    public List<StatEntry> top(StatType type, int limit) {
        return store.top(type, limit);
    }

    @Override
    public List<io.github.aliensockpuppet.dorkstats.api.KdrEntry> topKdr(int limit) {
        return store.topKdr(limit);
    }

    @Override
    public void recordCombatLogKill(UUID killer, String killerName, UUID victim, String victimName) {
        if (victim != null) {
            store.increment(victim, victimName, StatType.DEATHS);
        }
        if (killer != null && !killer.equals(victim)) {
            store.increment(killer, killerName, StatType.KILLS);
        }
    }
}
