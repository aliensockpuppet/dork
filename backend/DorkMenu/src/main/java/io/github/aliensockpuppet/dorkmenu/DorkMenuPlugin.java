package io.github.aliensockpuppet.dorkmenu;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.dorkmenu.command.MenuCommand;
import io.github.aliensockpuppet.dorkmenu.config.ConfigFiles;
import io.github.aliensockpuppet.dorkmenu.menu.MenuService;

public final class DorkMenuPlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;
    private static final int CONFIG_VERSION = 10;

    private volatile FileConfiguration cfg;
    private DorkCoreApi core;
    private MenuService menu;

    @Override
    public void onEnable() {
        DorkCoreApi fetched = fetchCore();
        if (fetched == null) {
            getLogger().severe("DorkCore is not available. DorkMenu is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (fetched.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + fetched.apiVersion() + " but DorkMenu needs v"
                    + BUILT_FOR_API + ". DorkMenu is off. Rebuild DorkMenu against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.core = fetched;

        reloadFiles();
        this.menu = new MenuService(this, core);

        MenuCommand command = new MenuCommand(this, core);
        getLifecycleManager().registerEventHandler(
                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, event -> {
                    var reg = event.registrar();
                    String label = cfg().getString("command.name", "dork");
                    if (label == null || label.isBlank()) {
                        label = "dork";
                    }
                    reg.register(label.trim().toLowerCase(java.util.Locale.ROOT), "Open the server menu.",
                            cfg().isList("command.aliases") ? cfg().getStringList("command.aliases") : java.util.List.of("menu"), command.asBasicCommand());
                    reg.register("rtpqueue", "Open the matchmaking queue.",
                            java.util.List.of("queue"), command.screenCommand("duel"));
                    reg.register("stats", "Your stats and the leaderboards.",
                            command.screenCommand("stats"));
                    reg.register("discord", "Get the Discord invite link.",
                            command.screenCommand("discord"));
                    reg.register("store", "Open the web store link.",
                            command.screenCommand("store"));
                });

        getServer().getPluginManager().registerEvents(
                new io.github.aliensockpuppet.dorkmenu.menu.MenuClickListener(this, menu), this);

        getLogger().info("DorkMenu enabled against DorkCore API v" + core.apiVersion() + ".");
    }

    @Override
    public void onDisable() {
    }

    public void reloadFiles() {
        cfg = ConfigFiles.load(this, "config.yml", CONFIG_VERSION);
    }

    public FileConfiguration cfg() {
        return cfg;
    }

    public MenuService menu() {
        return menu;
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
