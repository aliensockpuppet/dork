package io.github.aliensockpuppet.dorksurvey;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

public final class DorkSurveyPlugin extends JavaPlugin {
    private static final int BUILT_FOR_API = 11;

    private DorkCoreApi core;
    private SurveyStore store;
    private SurveyManager survey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.core = fetchCore();
        if (core == null) {
            getLogger().severe("DorkCore is not available. DorkSurvey is off.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (core.apiVersion() != BUILT_FOR_API) {
            getLogger().severe("DorkCore API is v" + core.apiVersion() + " but DorkSurvey needs v"
                    + BUILT_FOR_API + ". DorkSurvey is off. Rebuild DorkSurvey against this Core.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.store = new SurveyStore(this);
        store.load();
        this.survey = new SurveyManager(this, core, store, Survey.load(getConfig()));

        SurveyCommand command = new SurveyCommand(this, core, survey);
        if (getCommand("survey") != null) {
            getCommand("survey").setExecutor(new CommandThrottle(core, "dorksurvey", "survey", command));
            getCommand("survey").setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new ReminderListener(this, survey), this);

        if (Survey.load(getConfig()).isEmpty()) {
            getLogger().warning("DorkSurvey enabled, but no questions are configured. Add some to config.yml.");
        } else {
            getLogger().info("DorkSurvey enabled.");
        }
    }

    public void reloadFromDisk() {
        reloadConfig();
        store.load();
        survey.setSurvey(Survey.load(getConfig()));
    }

    public DorkCoreApi core() {
        return core;
    }

    public SurveyStore store() {
        return store;
    }

    private DorkCoreApi fetchCore() {
        RegisteredServiceProvider<DorkCoreApi> rsp =
                getServer().getServicesManager().getRegistration(DorkCoreApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
