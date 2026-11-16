package io.github.aliensockpuppet.dorkcombat;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.CombatTag;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

public final class DorkCombatPlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;

    private CombatManager combat;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DorkCoreApi core = fetchCore();
        if (core == null) {
            getLogger().severe("DorkCore is not available. DorkCombat is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (core.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + core.apiVersion() + " but DorkCombat needs v"
                    + BUILT_FOR_API + ". DorkCombat is off. Rebuild DorkCombat against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long tagSeconds = Math.max(1L, getConfig().getLong("combat.tag-seconds", 15));
        boolean tagExplosions = getConfig().getBoolean("combat.tag-explosions", true);
        boolean exemptKicks = getConfig().getBoolean("combat.exempt-kicks", false);
        this.combat = new CombatManager(this, core, tagSeconds, tagExplosions, exemptKicks);
        combat.start();

        getServer().getServicesManager().register(CombatTag.class, combat, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new CombatListener(this, combat, core), this);

        getLogger().info("DorkCombat enabled. Combat tag is " + tagSeconds + "s, registered with DorkCore.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (combat != null) {
            combat.stop();
        }
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
