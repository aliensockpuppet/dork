package io.github.aliensockpuppet.dorkcore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.dorkcore.api.Homes;
import io.github.aliensockpuppet.dorkcore.command.AnonMessageCommand;
import io.github.aliensockpuppet.dorkcore.command.BackCommand;
import io.github.aliensockpuppet.dorkcore.command.ChatListener;
import io.github.aliensockpuppet.dorkcore.command.CommandThrottle;
import io.github.aliensockpuppet.dorkcore.command.CoreServices;
import io.github.aliensockpuppet.dorkcore.command.Guard;
import io.github.aliensockpuppet.dorkcore.command.HomeCommand;
import io.github.aliensockpuppet.dorkcore.command.PlayerListener;
import io.github.aliensockpuppet.dorkcore.command.IgnoreCommand;
import io.github.aliensockpuppet.dorkcore.command.MessageCommand;
import io.github.aliensockpuppet.dorkcore.command.PhantomListener;
import io.github.aliensockpuppet.dorkcore.command.SpawnCommand;
import io.github.aliensockpuppet.dorkcore.command.SuicideCommand;
import io.github.aliensockpuppet.dorkcore.command.Teleports;
import io.github.aliensockpuppet.dorkcore.command.TpaCommand;
import io.github.aliensockpuppet.dorkcore.command.ToggleCommand;
import io.github.aliensockpuppet.dorkcore.command.TpaDialogListener;
import io.github.aliensockpuppet.dorkcore.command.TpaManager;
import io.github.aliensockpuppet.dorkcore.internal.ConfigFiles;
import io.github.aliensockpuppet.dorkcore.internal.CoreConfig;
import io.github.aliensockpuppet.dorkcore.internal.store.Database;
import io.github.aliensockpuppet.dorkcore.internal.store.HomeStore;
import io.github.aliensockpuppet.dorkcore.internal.store.IgnoreStore;
import io.github.aliensockpuppet.dorkcore.internal.store.SpawnStore;
import io.github.aliensockpuppet.dorkcore.internal.store.ToggleStore;
import io.github.aliensockpuppet.dorkcore.service.AuthGateImpl;
import io.github.aliensockpuppet.dorkcore.service.BackTracker;
import io.github.aliensockpuppet.dorkcore.service.ColorsImpl;
import io.github.aliensockpuppet.dorkcore.service.CombatTagImpl;
import io.github.aliensockpuppet.dorkcore.service.CooldownsImpl;
import io.github.aliensockpuppet.dorkcore.service.DorkCoreApiImpl;
import io.github.aliensockpuppet.dorkcore.service.HomesImpl;
import io.github.aliensockpuppet.dorkcore.service.MessagesImpl;
import io.github.aliensockpuppet.dorkcore.service.PlayerTogglesImpl;
import io.github.aliensockpuppet.dorkcore.service.SoundsImpl;

import java.sql.SQLException;

public final class DorkCorePlugin extends JavaPlugin {
    private static final int MESSAGES_VERSION = 6;
    private static final int SOUNDS_VERSION = 3;
    private static final int CONFIG_VERSION = 4;

    private MessagesImpl messages;
    private SoundsImpl sounds;
    private final CoreConfig config = new CoreConfig();
    private Database database;
    private PlayerTogglesImpl playerToggles;
    private CoreServices services;

    @Override
    public void onEnable() {
        MiniMessage mini = MiniMessage.miniMessage();

        this.messages = new MessagesImpl(mini);
        this.sounds = new SoundsImpl();
        loadConfigs();

        ColorsImpl colors = new ColorsImpl(mini);
        CooldownsImpl cooldowns = new CooldownsImpl(config.cooldownSettings());
        CombatTagImpl combatTag = new CombatTagImpl();
        AuthGateImpl authGate = new AuthGateImpl(config.get().failClosedWhenAuthMissing());

        this.database = new Database(this);
        try {
            database.open();
        } catch (SQLException e) {
            getLogger().severe("Could not open the data file. Homes and spawn are off this run,"
                    + " and toggles are memory-only: " + e.getMessage());
            database = null;
        }

        ToggleStore toggleStore = database == null ? null : new ToggleStore(database);
        this.playerToggles = new PlayerTogglesImpl(this, toggleStore);

        messages.setToggles(playerToggles);
        sounds.setToggles(playerToggles);

        HomeStore homeStore = database == null ? null : new HomeStore(database);
        Homes homesView = homeStore == null ? player -> java.util.List.of()
                : new HomesImpl(this, homeStore);

        DorkCoreApi api = new DorkCoreApiImpl(messages, sounds, colors, cooldowns, combatTag, authGate,
                playerToggles, homesView);
        getServer().getServicesManager().register(DorkCoreApi.class, api, this, ServicePriority.Normal);

        if (database != null) {
            wireCommands(colors, cooldowns, combatTag, authGate, playerToggles, homeStore);
        }

        getLogger().info("DorkCore enabled. Shared services registered (API v" + DorkCoreApi.API_VERSION + ").");
    }

    private void wireCommands(ColorsImpl colors, CooldownsImpl cooldowns,
                              CombatTagImpl combatTag, AuthGateImpl authGate,
                              PlayerTogglesImpl playerToggles, HomeStore homes) {
        SpawnStore spawn = new SpawnStore(database);
        IgnoreStore ignores = new IgnoreStore(database);
        BackTracker back = new BackTracker();

        this.services = new CoreServices(
                this, messages, sounds, colors, cooldowns, combatTag, authGate,
                playerToggles, config, homes, spawn, ignores, back);

        autoSetSpawnOnFirstLoad(spawn);

        Guard guard = new Guard(services);
        Teleports teleports = new Teleports(services);
        TpaManager tpa = new TpaManager();

        SpawnCommand spawnCmd = new SpawnCommand(services, guard, teleports);
        BackCommand backCmd = new BackCommand(services, guard, teleports);
        HomeCommand homeCmd = new HomeCommand(services, guard, teleports);
        TpaCommand tpaCmd = new TpaCommand(services, guard, teleports, tpa);
        SuicideCommand suicideCmd = new SuicideCommand(services, guard);
        MessageCommand msgCmd = new MessageCommand(services);
        IgnoreCommand ignoreCmd = new IgnoreCommand(services);

        bind("spawn", spawnCmd);
        bind("setspawn", spawnCmd);
        bind("back", backCmd);
        bind("home", homeCmd);
        bind("sethome", homeCmd);
        bind("delhome", homeCmd);
        bind("homes", homeCmd);
        bind("tpa", tpaCmd);
        bind("tpahere", tpaCmd);
        bind("tpaccept", tpaCmd);
        bind("tpdeny", tpaCmd);
        bind("tpcancel", tpaCmd);
        ToggleCommand toggleCmd = new ToggleCommand(services);
        bind("toggle", toggleCmd);
        bind("suicide", suicideCmd);
        bind("msg", msgCmd);
        bind("r", msgCmd);
        bind("anonmsg", new AnonMessageCommand(services));
        bind("ignore", ignoreCmd);
        bind("unignore", ignoreCmd);
        bind("ignorelist", ignoreCmd);

        getServer().getPluginManager().registerEvents(new PlayerListener(services, tpa, msgCmd), this);
        getServer().getPluginManager().registerEvents(new PhantomListener(services), this);
        getServer().getPluginManager().registerEvents(new ChatListener(services), this);
        getServer().getPluginManager().registerEvents(new TpaDialogListener(this, tpaCmd), this);
    }

    private void autoSetSpawnOnFirstLoad(SpawnStore spawn) {
        if (!config.get().autoSetSpawnOnFirstLoad()) {
            return;
        }
        try {
            if (spawn.isSet()) {
                return;
            }
        } catch (SQLException e) {
            getLogger().warning("Could not check for an existing spawn; skipping auto-set: " + e.getMessage());
            return;
        }

        org.bukkit.World world = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
        if (world == null) {
            getLogger().warning("No world is loaded; cannot auto-set spawn.");
            return;
        }

        org.bukkit.Location current = world.getSpawnLocation();
        int bx = current.getBlockX();
        int by = current.getBlockY();
        int bz = current.getBlockZ();
        org.bukkit.Location spawnPoint = new org.bukkit.Location(world, bx + 0.5, by, bz + 0.5, 0f, 0f);

        world.setSpawnLocation(bx, by, bz);

        try {
            spawn.set(io.github.aliensockpuppet.dorkcore.internal.store.StoredLocation.of(spawnPoint));
            getLogger().info("First load: server spawn set to " + bx + ", " + by + ", " + bz
                    + " in world '" + world.getName() + "'. SpawnPlatform will center its floor here.");
        } catch (SQLException e) {
            getLogger().warning("Could not save the auto-set spawn: " + e.getMessage());
        }
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command " + name + " is missing from plugin.yml.");
            return;
        }
        CommandThrottle throttled = new CommandThrottle(services, name, executor);
        command.setExecutor(throttled);
        command.setTabCompleter(throttled);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (database != null) {
            database.close();
        }
    }

    public void loadConfigs() {
        messages.load(ConfigFiles.load(this, "messages.yml", MESSAGES_VERSION));
        sounds.load(ConfigFiles.load(this, "sounds.yml", SOUNDS_VERSION));
        config.load(ConfigFiles.load(this, "config.yml", CONFIG_VERSION));
    }
}
