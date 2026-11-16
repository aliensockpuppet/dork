package io.github.aliensockpuppet.dorkmotd.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "dorkmotd",
        name = "DorkMotd",
        version = "1.0.0",
        description = "Global server-list MOTD for the proxy, with rotation and a player-set line.",
        authors = {"aliensockpuppet"}
)
public final class DorkMotdVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private Map<String, Object> config = new LinkedHashMap<>();
    private MotdStore store;
    private MotdManager motd;
    private ScheduledTask rotationTask;

    @Inject
    public DorkMotdVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        loadConfig();

        this.store = new MotdStore(dataDir, logger);
        store.load();
        this.motd = buildManager();
        applyFormat();
        motd.rebuildCache();
        applyRotation();

        proxy.getEventManager().register(this, new PingListener(motd));

        proxy.getChannelRegistrar().register(BackendBridge.SET, BackendBridge.RESULT);
        proxy.getEventManager().register(this, new BackendBridge(this, motd));

        CommandManager cm = proxy.getCommandManager();
        CommandMeta meta = cm.metaBuilder("motd").plugin(this).build();
        cm.register(meta, new MotdCommand(this, motd));

        logger.info("DorkMotd enabled (Velocity). Backend MOTD bridge active.");
    }

    private MotdManager buildManager() {
        int maxLength = intCfg("motd.max-length", 100);
        return new MotdManager(store, maxLength,
                cooldownMin("global", 240), cooldownMax("global", 360),
                cooldownMin("player", 1500), cooldownMax("player", 2100));
    }

    private long cooldownMin(String name, long def) {
        Object range = valueAt("cooldowns." + name + "-min-seconds");
        if (range instanceof Number n) {
            return n.longValue();
        }
        return longCfg("cooldowns." + name + "-seconds", def);
    }

    private long cooldownMax(String name, long def) {
        Object range = valueAt("cooldowns." + name + "-max-seconds");
        if (range instanceof Number n) {
            return n.longValue();
        }
        return longCfg("cooldowns." + name + "-seconds", def);
    }

    private void applyFormat() {
        motd.configureFormat(
                stringCfg("format.header", "<green>A Minecraft Server"),
                boolCfg("format.quote-player", true));
    }

    private void applyRotation() {
        motd.configureRotation(
                boolCfg("rotation.enabled", false),
                boolCfg("rotation.random", false),
                stringListCfg("rotation.motds"));
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
        if (!motd.rotationActive()) {
            return;
        }
        long interval = Math.max(1L, longCfg("rotation.interval-seconds", 60));
        rotationTask = proxy.getScheduler().buildTask(this, motd::advanceRotation)
                .delay(interval, TimeUnit.SECONDS)
                .repeat(interval, TimeUnit.SECONDS)
                .schedule();
    }

    public void reloadFromDisk() {
        loadConfig();
        store.load();
        motd.reconfigure(
                intCfg("motd.max-length", 100),
                cooldownMin("global", 240), cooldownMax("global", 360),
                cooldownMin("player", 1500), cooldownMax("player", 2100));
        applyFormat();
        motd.rebuildCache();
        applyRotation();
    }

    public MotdStore store() {
        return store;
    }

    public boolean requirePermission() {
        return boolCfg("motd.require-permission", false);
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public String message(String key) {
        Object v = valueAt("messages." + key);
        if (v != null) {
            return String.valueOf(v);
        }
        return switch (key) {
            case "no-permission" -> "<red>You cannot do that";
            case "players-only" -> "<red>Only a player can set the MOTD";
            case "usage" -> "<gray>Usage: /motd <text>";
            case "motd-set" -> "<green>MOTD updated";
            case "motd-reset" -> "<green>MOTD reset to default";
            case "motd-reloaded" -> "<green>DorkMotd config reloaded";
            case "motd-global-cooldown" -> "<red>The MOTD was changed recently, try again in %seconds%s";
            case "motd-player-cooldown" -> "<red>You can change the MOTD again in %seconds%s";
            case "motd-invalid" -> "<red>That MOTD has a broken format tag";
            case "motd-empty" -> "<red>The MOTD cannot be empty";
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path cfg = dataDir.resolve("config.yml");
            if (!Files.exists(cfg)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, cfg);
                    }
                }
            }
            if (Files.exists(cfg)) {
                try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml().load(r);
                    this.config = (loaded instanceof Map) ? (Map<String, Object>) loaded : new LinkedHashMap<>();
                }
            } else {
                this.config = new LinkedHashMap<>();
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not load DorkMotd config.yml, using defaults", e);
            this.config = new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Object valueAt(String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        Object node = config;
        for (String part : parts) {
            if (!(node instanceof Map<?, ?> m)) {
                return null;
            }
            node = ((Map<String, Object>) m).get(part);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private String stringCfg(String key, String def) {
        Object v = valueAt(key);
        return v == null ? def : String.valueOf(v);
    }

    private int intCfg(String key, int def) {
        Object v = valueAt(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return def;
    }

    private long longCfg(String key, long def) {
        Object v = valueAt(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return def;
    }

    private boolean boolCfg(String key, boolean def) {
        Object v = valueAt(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListCfg(String key) {
        Object v = valueAt(key);
        if (v instanceof List<?> list) {
            List<String> out = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return List.of();
    }
}
