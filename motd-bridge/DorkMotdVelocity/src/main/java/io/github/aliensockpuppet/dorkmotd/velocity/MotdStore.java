package io.github.aliensockpuppet.dorkmotd.velocity;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MotdStore {
    private final Path file;
    private final Logger logger;
    private final Yaml yaml;
    private Map<String, Object> data = new LinkedHashMap<>();

    public MotdStore(Path dataDir, Logger logger) {
        this.file = dataDir.resolve("data.yml");
        this.logger = logger;
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(opts);
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                this.data = new LinkedHashMap<>();
                return;
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Object loaded = yaml.load(r);
                this.data = (loaded instanceof Map) ? (Map<String, Object>) loaded : new LinkedHashMap<>();
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not load DorkMotd data.yml, starting empty", e);
            this.data = new LinkedHashMap<>();
        }
    }

    public synchronized String motd() {
        Object v = data.get("motd");
        return v == null ? "" : String.valueOf(v);
    }

    public synchronized long lastGlobalMillis() {
        return asLong(data.get("last-global-millis"));
    }

    @SuppressWarnings("unchecked")
    public synchronized long lastPlayerMillis(UUID player) {
        Object players = data.get("players");
        if (players instanceof Map<?, ?> m) {
            return asLong(((Map<String, Object>) m).get(player.toString()));
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    public synchronized void recordChange(String motd, UUID player, long nowMillis) {
        data.put("motd", motd);
        data.put("last-global-millis", nowMillis);
        Object players = data.get("players");
        Map<String, Object> map;
        if (players instanceof Map<?, ?> m) {
            map = (Map<String, Object>) m;
        } else {
            map = new LinkedHashMap<>();
            data.put("players", map);
        }
        map.put(player.toString(), nowMillis);
        save();
    }

    public synchronized void clearMotd() {
        data.put("motd", "");
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                yaml.dump(data, w);
            }
            try {
                Files.move(tmp, file,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warn("Could not save DorkMotd data.yml", e);
        }
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
