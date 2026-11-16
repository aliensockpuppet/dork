package io.github.aliensockpuppet.dorkstats;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.dorkstats.api.DorkStatsApi;
import io.github.aliensockpuppet.dorkstats.data.StatStore;
import io.github.aliensockpuppet.dorkstats.listener.StatListener;
import io.github.aliensockpuppet.dorkstats.papi.StatsExpansion;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DorkStatsPlugin extends JavaPlugin implements TabExecutor {
    private static final int BUILT_FOR_API = 11;

    private DorkCoreApi core;
    private StatStore store;
    private BukkitTask saveTask;
    private BukkitTask rebuildTask;
    private int leaderboardSize = 10;

    @Override
    public void onEnable() {
        DorkCoreApi fetched = fetchCore();
        if (fetched == null) {
            getLogger().severe("DorkCore is not available. DorkStats is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (fetched.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + fetched.apiVersion() + " but DorkStats needs v"
                    + BUILT_FOR_API + ". DorkStats is off. Rebuild it against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.core = fetched;

        saveDefaultConfig();
        store = new StatStore(this);
        store.load();
        applyConfig();
        store.rebuildTop(leaderboardSize);

        getServer().getPluginManager().registerEvents(new StatListener(store), this);
        getServer().getServicesManager().register(DorkStatsApi.class, new ApiImpl(store), this, ServicePriority.Normal);

        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            store.startSession(online.getUniqueId(), online.getName());
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new StatsExpansion(store, getPluginMeta().getVersion()).register();
                getLogger().info("Hooked into PlaceholderAPI (dorkstats).");
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholders are off; the API and leaderboards still work.");
        }

        PluginCommand cmd = Objects.requireNonNull(getCommand("dorkstats"), "dorkstats missing from plugin.yml");
        cmd.setExecutor(new CommandThrottle(core, "dorkstats", "dorkstats", this));
        cmd.setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (store != null) {
            store.saveNow();
        }
    }

    private void applyConfig() {
        cancelTasks();
        leaderboardSize = Math.max(1, Math.min(50, getConfig().getInt("leaderboard-size", 10)));
        long saveEvery = Math.max(30L, getConfig().getLong("save-interval-seconds", 300L)) * 20L;
        long rebuildEvery = Math.max(10L, getConfig().getLong("leaderboard-refresh-seconds", 60L)) * 20L;
        saveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, store::saveNow, saveEvery, saveEvery);
        rebuildTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> store.rebuildTop(leaderboardSize), rebuildEvery, rebuildEvery);
    }

    private void cancelTasks() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (rebuildTask != null) {
            rebuildTask.cancel();
            rebuildTask = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dorkstats.admin")) {
                core.messages().send(sender, "no-permission");
                return true;
            }
            reloadConfig();
            applyConfig();
            store.rebuildTop(leaderboardSize);
            core.messages().send(sender, "stats-reloaded");
            return true;
        }
        core.messages().send(sender, "stats-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("dorkstats.admin")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
