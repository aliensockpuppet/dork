package io.github.aliensockpuppet.dorkstats.data;

import io.github.aliensockpuppet.dorkstats.api.StatType;

import java.util.concurrent.atomic.AtomicLong;

public final class PlayerStats {
    private volatile String name;
    private final AtomicLong[] counts;

    public PlayerStats(String name) {
        this.name = name;
        this.counts = new AtomicLong[StatType.values().length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = new AtomicLong();
        }
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    public long get(StatType type) {
        return counts[type.ordinal()].get();
    }

    public void set(StatType type, long value) {
        counts[type.ordinal()].set(value);
    }

    public void increment(StatType type) {
        counts[type.ordinal()].incrementAndGet();
    }

    public void add(StatType type, long amount) {
        if (amount > 0) {
            counts[type.ordinal()].addAndGet(amount);
        }
    }
}
