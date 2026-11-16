package io.github.aliensockpuppet.spawnplatform;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.spawnplatform.command.CommandThrottle;
import io.github.aliensockpuppet.spawnplatform.command.SpawnPlatformCommand;
import io.github.aliensockpuppet.spawnplatform.config.Settings;
import io.github.aliensockpuppet.spawnplatform.platform.LiquidGuard;
import io.github.aliensockpuppet.spawnplatform.platform.PlatformService;

import java.util.Objects;
import java.util.logging.Level;

public final class SpawnPlatformPlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;

    private volatile Settings settings;
    private DorkCoreApi core;
    private PlatformService service;
    private BukkitTask clearTask;
    private LiquidGuard liquidGuard;

    @Override
    public void onEnable() {
        DorkCoreApi fetched = fetchCore();
        if (fetched == null) {
            getLogger().severe("DorkCore is not available. SpawnPlatform is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (fetched.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + fetched.apiVersion() + " but SpawnPlatform needs v"
                    + BUILT_FOR_API + ". SpawnPlatform is off. Rebuild it against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.core = fetched;

        saveDefaultConfig();
        service = new PlatformService(this, core);
        settings = Settings.from(getConfig(), getLogger());

        liquidGuard = new LiquidGuard(this);
        getServer().getPluginManager().registerEvents(liquidGuard, this);

        SpawnPlatformCommand cmd = new SpawnPlatformCommand(this, core);
        PluginCommand command = Objects.requireNonNull(getCommand("spawnplatform"), "spawnplatform missing from plugin.yml");
        command.setExecutor(new CommandThrottle(core, "spawnplatform", "spawnplatform", cmd));
        command.setTabCompleter(cmd);

        scheduleClear();

        if (settings.runOnStart()) {
            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    service.buildPlatform(settings);
                    service.clearArea(settings);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Start-up build/clear failed", e);
                }
            }, 20L);
        }

        getLogger().info("SpawnPlatform enabled against DorkCore API v" + core.apiVersion() + ".");
    }

    @Override
    public void onDisable() {
        if (clearTask != null) {
            clearTask.cancel();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
        if (liquidGuard != null) {
            liquidGuard.refresh();
        }
        scheduleClear();
    }

    private void scheduleClear() {
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }
        if (!settings.clearEnabled()) {
            return;
        }
        long every = settings.clearIntervalTicks();
        clearTask = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                service.clearArea(settings);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Scheduled clear failed", e);
            }
        }, every, every);
    }

    public void setCenterHere(Player player) {
        getConfig().set("world", player.getWorld().getName());
        getConfig().set("use-world-spawn", false);
        getConfig().set("center-x", player.getLocation().getBlockX());
        getConfig().set("center-z", player.getLocation().getBlockZ());
        saveConfig();
        reloadSettings();
    }

    public Settings settings() {
        return settings;
    }

    public PlatformService service() {
        return service;
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
