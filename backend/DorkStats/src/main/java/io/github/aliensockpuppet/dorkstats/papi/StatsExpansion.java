package io.github.aliensockpuppet.dorkstats.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import io.github.aliensockpuppet.dorkstats.api.StatEntry;
import io.github.aliensockpuppet.dorkstats.api.StatType;
import io.github.aliensockpuppet.dorkstats.data.StatStore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class StatsExpansion extends PlaceholderExpansion {
    private final StatStore store;
    private final String version;

    public StatsExpansion(StatStore store, String version) {
        this.store = store;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "dorkstats";
    }

    @Override
    public String getAuthor() {
        return "aliensockpuppet";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String p = params.toLowerCase(Locale.ROOT);

        if (p.startsWith("top_")) {
            return topPlaceholder(p);
        }

        if (player == null) {
            return "";
        }
        if (p.equals("kdr")) {
            return String.format(Locale.ROOT, "%.2f", store.kdr(player.getUniqueId()));
        }
        if (p.equals("playtime_raw")) {
            return Long.toString(store.value(player.getUniqueId(), StatType.PLAYTIME));
        }
        Optional<StatType> type = StatType.fromId(p);
        return type.map(t -> t.format(store.value(player.getUniqueId(), t))).orElse("");
    }

    private String topPlaceholder(String p) {
        String[] parts = p.split("_");
        if (parts.length != 4) {
            return "";
        }
        Optional<StatType> type = StatType.fromId(parts[1]);
        String field = parts[2];
        int n;
        try {
            n = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return "";
        }
        if (type.isEmpty() || n < 1) {
            return "";
        }
        List<StatEntry> list = store.top(type.get(), n);
        if (n > list.size()) {
            return field.equals("value") ? type.get().format(0L) : "";
        }
        StatEntry entry = list.get(n - 1);
        return field.equals("value") ? type.get().format(entry.value()) : entry.name();
    }
}
