package io.github.aliensockpuppet.rtpqueue;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi;
import io.github.aliensockpuppet.rtpqueue.command.AdminCommand;
import io.github.aliensockpuppet.rtpqueue.command.CommandThrottle;
import io.github.aliensockpuppet.rtpqueue.command.QueueCommand;
import io.github.aliensockpuppet.rtpqueue.command.RtpCommand;
import io.github.aliensockpuppet.rtpqueue.config.ConfigFiles;
import io.github.aliensockpuppet.rtpqueue.config.Settings;
import io.github.aliensockpuppet.rtpqueue.gear.GearScanner;
import io.github.aliensockpuppet.rtpqueue.listener.PlayerListener;
import io.github.aliensockpuppet.rtpqueue.queue.QueueManager;
import io.github.aliensockpuppet.rtpqueue.rtp.LocationFinder;
import io.github.aliensockpuppet.rtpqueue.rtp.RespawnPool;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class RTPQueuePlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;
    private static final int CONFIG_VERSION = 3;

    private volatile Settings settings;
    private DorkCoreApi core;
    private QueueManager queue;
    private BukkitTask matchTask;
    private BukkitTask actionBarTask;
    private YamlConfiguration rawConfig;
    private RespawnPool respawnPool;

    @Override
    public void onEnable() {
        DorkCoreApi fetched = fetchCore();
        if (fetched == null) {
            getLogger().severe("DorkCore is not available. RTPQueue is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (fetched.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + fetched.apiVersion() + " but RTPQueue needs v"
                    + BUILT_FOR_API + ". RTPQueue is off. Rebuild RTPQueue against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.core = fetched;

        loadFiles();
        queue = new QueueManager(this, new GearScanner(), new LocationFinder(this));
        respawnPool = new RespawnPool(this, new LocationFinder(this));
        getServer().getPluginManager().registerEvents(respawnPool, this);
        respawnPool.configure(rawConfig);

        QueueCommand queueCommand = new QueueCommand(this);
        PluginCommand rtpqueue = Objects.requireNonNull(getCommand("rtpqueue"), "rtpqueue missing from plugin.yml");
        rtpqueue.setExecutor(new CommandThrottle(core, "rtpqueue", "rtpqueue", queueCommand));
        rtpqueue.setTabCompleter(queueCommand);

        AdminCommand adminCommand = new AdminCommand(this);
        PluginCommand admin = Objects.requireNonNull(getCommand("rtpqueue-admin"), "rtpqueue-admin missing from plugin.yml");
        admin.setExecutor(new CommandThrottle(core, "rtpqueue", "rtpqueue-admin", adminCommand));
        admin.setTabCompleter(adminCommand);

        RtpCommand rtpCommand = new RtpCommand(this);
        PluginCommand rtp = Objects.requireNonNull(getCommand("rtp"), "rtp missing from plugin.yml");
        rtp.setExecutor(new CommandThrottle(core, "rtpqueue", "rtp", rtpCommand));
        rtp.setTabCompleter(rtpCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getServicesManager().register(RTPQueueApi.class, new ApiImpl(this), this, ServicePriority.Normal);
        startMatchmaker();
        startActionBar();

        getLogger().info("RTPQueue enabled against DorkCore API v" + core.apiVersion() + ".");
    }

    @Override
    public void onDisable() {
        if (matchTask != null) {
            matchTask.cancel();
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (queue != null) {
            queue.shutdown();
        }
        if (respawnPool != null) {
            respawnPool.shutdown();
        }
    }

    private void loadFiles() {
        YamlConfiguration config = ConfigFiles.load(this, "config.yml", CONFIG_VERSION);
        rawConfig = config;
        settings = Settings.from(config, getLogger());
    }

    public void reloadAll() {
        loadFiles();
        queue.onReload(settings);
        if (respawnPool != null) {
            respawnPool.configure(rawConfig);
        }
        startMatchmaker();
    }

    private void startMatchmaker() {
        if (matchTask != null) {
            matchTask.cancel();
        }
        int every = settings.matchmakingIntervalTicks();
        matchTask = getServer().getScheduler().runTaskTimer(this, this::runMatchmakerSafely, every, every);
    }

    private void startActionBar() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        long every = 30L;
        actionBarTask = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                queue.sendStatusActionBars();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Queue action bar refresh failed", e);
            }
        }, every, every);
    }

    private void runMatchmakerSafely() {
        try {
            queue.matchmake();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Matchmaking run failed", e);
        }
    }

    public void scheduleMatchmake() {
        sync(() -> getServer().getScheduler().runTask(this, this::runMatchmakerSafely));
    }

    public boolean sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            if (!isEnabled()) {
                return false;
            }
            task.run();
            return true;
        }
        if (!isEnabled()) {
            return false;
        }
        try {
            getServer().getScheduler().runTask(this, task);
            return true;
        } catch (IllegalPluginAccessException e) {
            return false;
        }
    }

    public void runCommands(List<String> commands, String... pairs) {
        String[] safe = sanitizePairs(pairs);
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String cmd = apply(raw.trim(), safe);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Hook command failed: " + cmd, e);
            }
        }
    }

    private static String apply(String line, String... pairs) {
        String out = line;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return out;
    }

    private static String[] sanitizePairs(String... pairs) {
        String[] out = pairs.clone();
        for (int i = 1; i < out.length; i += 2) {
            out[i] = sanitizeForCommand(out[i]);
        }
        return out;
    }

    private static String sanitizeForCommand(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public Settings settings() {
        return settings;
    }

    public DorkCoreApi core() {
        return core;
    }

    public QueueManager queue() {
        return queue;
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
