package io.github.aliensockpuppet.dorkstats.api;

import java.util.Locale;
import java.util.Optional;

public enum StatType {
    KILLS("Kills", false),
    DEATHS("Deaths", false),
    PLACED("Blocks Placed", false),
    BROKEN("Blocks Broken", false),
    PLAYTIME("Playtime", true);

    private final String display;
    private final boolean duration;

    StatType(String display, boolean duration) {
        this.display = display;
        this.duration = duration;
    }

    public String display() {
        return display;
    }

    public boolean isDuration() {
        return duration;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String format(long value) {
        return duration ? formatDuration(value) : Long.toString(value);
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        int units = 0;
        if (days > 0) {
            sb.append(days).append('d');
            units++;
        }
        if (hours > 0 && units < 2) {
            if (units > 0) {
                sb.append(' ');
            }
            sb.append(hours).append('h');
            units++;
        }
        if (minutes > 0 && units < 2) {
            if (units > 0) {
                sb.append(' ');
            }
            sb.append(minutes).append('m');
            units++;
        }
        if (secs > 0 && units < 2) {
            if (units > 0) {
                sb.append(' ');
            }
            sb.append(secs).append('s');
        }
        return sb.toString();
    }

    public static Optional<StatType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (StatType t : values()) {
            if (t.id().equals(id.toLowerCase(Locale.ROOT))) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
