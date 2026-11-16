package io.github.aliensockpuppet.dorkstats.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkstats.api.StatEntry;
import io.github.aliensockpuppet.dorkstats.api.StatType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerStats> players = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();

    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();

    private volatile Map<StatType, List<StatEntry>> top = emptyTop();

    private volatile List<io.github.aliensockpuppet.dorkstats.api.KdrEntry> topKdr = List.of();

    public StatStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    private static Map<StatType, List<StatEntry>> emptyTop() {
        Map<StatType, List<StatEntry>> m = new EnumMap<>(StatType.class);
        for (StatType t : StatType.values()) {
            m.put(t, List.of());
        }
        return m;
    }

    public void load() {
        players.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping bad UUID in stats.yml: " + key);
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            PlayerStats ps = new PlayerStats(sec.getString("name", key));
            for (StatType t : StatType.values()) {
                ps.set(t, Math.max(0L, sec.getLong(t.id(), 0L)));
            }
            players.put(id, ps);
        }
    }

    public void increment(UUID id, String name, StatType type) {
        PlayerStats ps = players.computeIfAbsent(id, k -> new PlayerStats(name));
        ps.name(name);
        ps.increment(type);
    }

    public void touch(UUID id, String name) {
        players.computeIfAbsent(id, k -> new PlayerStats(name)).name(name);
    }

    public void startSession(UUID id, String name) {
        touch(id, name);
        flush(id);
        sessions.put(id, System.currentTimeMillis());
    }

    public void flush(UUID id) {
        sessions.computeIfPresent(id, (key, anchor) -> {
            long now = System.currentTimeMillis();
            long elapsedSeconds = (now - anchor) / 1000L;
            if (elapsedSeconds <= 0) {
                return anchor;
            }
            PlayerStats ps = players.get(id);
            if (ps != null) {
                ps.add(StatType.PLAYTIME, elapsedSeconds);
            }
            return anchor + elapsedSeconds * 1000L;
        });
    }

    public void endSession(UUID id) {
        flush(id);
        sessions.remove(id);
    }

    public void flushAll() {
        for (UUID id : sessions.keySet()) {
            flush(id);
        }
    }

    public long value(UUID id, StatType type) {
        PlayerStats ps = players.get(id);
        return ps == null ? 0L : ps.get(type);
    }

    public double kdr(UUID id) {
        long k = value(id, StatType.KILLS);
        long d = value(id, StatType.DEATHS);
        return d <= 0 ? (double) k : (double) k / (double) d;
    }

    public List<StatEntry> top(StatType type, int limit) {
        List<StatEntry> list = top.getOrDefault(type, List.of());
        if (limit >= list.size()) {
            return list;
        }
        return List.copyOf(list.subList(0, Math.max(0, limit)));
    }

    public List<io.github.aliensockpuppet.dorkstats.api.KdrEntry> topKdr(int limit) {
        List<io.github.aliensockpuppet.dorkstats.api.KdrEntry> list = topKdr;
        if (limit >= list.size()) {
            return list;
        }
        return List.copyOf(list.subList(0, Math.max(0, limit)));
    }

    public void rebuildTop(int size) {
        flushAll();
        Map<StatType, List<StatEntry>> next = new EnumMap<>(StatType.class);
        for (StatType t : StatType.values()) {
            List<StatEntry> rows = new ArrayList<>(players.size());
            for (PlayerStats ps : players.values()) {
                long v = ps.get(t);
                if (v > 0) {
                    rows.add(new StatEntry(ps.name(), v));
                }
            }
            rows.sort(Comparator.comparingLong(StatEntry::value).reversed());
            if (rows.size() > size) {
                rows = new ArrayList<>(rows.subList(0, size));
            }
            next.put(t, List.copyOf(rows));
        }
        this.top = next;

        List<io.github.aliensockpuppet.dorkstats.api.KdrEntry> kdrRows = new ArrayList<>(players.size());
        for (PlayerStats ps : players.values()) {
            long kills = ps.get(StatType.KILLS);
            if (kills <= 0) {
                continue;
            }
            long deaths = ps.get(StatType.DEATHS);
            double ratio = deaths <= 0 ? (double) kills : (double) kills / (double) deaths;
            kdrRows.add(new io.github.aliensockpuppet.dorkstats.api.KdrEntry(ps.name(), ratio, kills, deaths));
        }
        kdrRows.sort(Comparator
                .comparingDouble(io.github.aliensockpuppet.dorkstats.api.KdrEntry::kdr).reversed()
                .thenComparing(Comparator.comparingLong(io.github.aliensockpuppet.dorkstats.api.KdrEntry::kills).reversed()));
        if (kdrRows.size() > size) {
            kdrRows = new ArrayList<>(kdrRows.subList(0, size));
        }
        this.topKdr = List.copyOf(kdrRows);
    }

    public void saveNow() {
        flushAll();
        synchronized (ioLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerStats> e : players.entrySet()) {
                String base = "players." + e.getKey() + ".";
                PlayerStats ps = e.getValue();
                yaml.set(base + "name", ps.name());
                for (StatType t : StatType.values()) {
                    yaml.set(base + t.id(), ps.get(t));
                }
            }
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            File tmp = new File(dir, "stats.yml.tmp");
            try {
                yaml.save(tmp);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save stats.yml: " + ex.getMessage());
            }
        }
    }
}
