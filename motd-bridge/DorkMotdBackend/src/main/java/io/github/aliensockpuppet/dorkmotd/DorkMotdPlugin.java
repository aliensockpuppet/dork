package io.github.aliensockpuppet.dorkmotd;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

public final class DorkMotdPlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;

    private ProxyBridge bridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DorkCoreApi core = fetchCore();
        if (core == null) {
            getLogger().severe("DorkCore is not available. DorkMotd is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (core.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + core.apiVersion() + " but DorkMotd needs v"
                    + BUILT_FOR_API + ". DorkMotd is off. Rebuild DorkMotd against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bridge = new ProxyBridge(this, core);

        getServer().getMessenger().registerOutgoingPluginChannel(this, ProxyBridge.CHANNEL_SET);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyBridge.CHANNEL_RESULT, bridge);

        MotdCommand command = new MotdCommand(this, core, bridge);
        if (getCommand("motd") != null) {
            getCommand("motd").setExecutor(new CommandThrottle(core, "dorkmotd", "motd", command));
            getCommand("motd").setTabCompleter(command);
        }

        getLogger().info("DorkMotd (backend) enabled. MOTD is forwarded to the proxy.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    public void reloadFromDisk() {
        reloadConfig();
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
