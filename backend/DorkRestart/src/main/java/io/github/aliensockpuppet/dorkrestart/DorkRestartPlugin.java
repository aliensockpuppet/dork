package io.github.aliensockpuppet.dorkrestart;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class DorkRestartPlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;

    private DorkCoreApi core;
    private RestartScheduler scheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.core = fetchCore();
        if (core == null) {
            getLogger().severe("DorkCore is not available. DorkRestart is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (core.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + core.apiVersion() + " but DorkRestart needs v"
                    + BUILT_FOR_API + ". DorkRestart is off. Rebuild DorkRestart against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.scheduler = buildScheduler();
        scheduler.start();

        RestartCommand command = new RestartCommand(this, core);
        if (getCommand("dorkrestart") != null) {
            getCommand("dorkrestart").setExecutor(new CommandThrottle(core, "dorkrestart", "dorkrestart", command));
            getCommand("dorkrestart").setTabCompleter(command);
        }

        getLogger().info("DorkRestart enabled.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    private RestartScheduler buildScheduler() {
        LocalTime time = parseTime(getConfig().getString("restart.time", "05:00"));
        boolean clearTags = getConfig().getBoolean("restart.clear-combat-tags", true);
        String method = getConfig().getString("restart.method", "shutdown");
        String command = getConfig().getString("restart.command", "");
        List<Integer> warnings = new ArrayList<>(getConfig().getIntegerList("restart.warnings"));
        if (warnings.isEmpty()) {
            warnings = List.of(300, 120, 60, 30, 10, 5, 4, 3, 2, 1);
        }
        return new RestartScheduler(this, core, time, clearTags, method, command, warnings,
                parseZone(getConfig().getString("restart.timezone", "")));
    }

    private java.time.ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.time.ZoneId.systemDefault();
        }
        try {
            return java.time.ZoneId.of(raw.trim());
        } catch (RuntimeException e) {
            getLogger().warning("restart.timezone '" + raw + "' is not a known zone. Using the server's own zone.");
            return java.time.ZoneId.systemDefault();
        }
    }

    private LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw == null ? "05:00" : raw.trim(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (RuntimeException e) {
            getLogger().warning("restart.time '" + raw + "' is not HH:mm. Using 05:00.");
            return LocalTime.of(5, 0);
        }
    }

    public void reloadFromDisk() {
        reloadConfig();
        if (scheduler != null) {
            scheduler.stop();
        }
        this.scheduler = buildScheduler();
        scheduler.start();
    }

    public RestartScheduler scheduler() {
        return scheduler;
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
