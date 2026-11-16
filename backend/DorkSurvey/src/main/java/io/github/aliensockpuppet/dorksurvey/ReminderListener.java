package io.github.aliensockpuppet.dorksurvey;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ReminderListener implements Listener {
    private final DorkSurveyPlugin plugin;
    private final SurveyManager survey;

    public ReminderListener(DorkSurveyPlugin plugin, SurveyManager survey) {
        this.plugin = plugin;
        this.survey = survey;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (plugin.store().rewarded(joined.getUniqueId())) {
            plugin.store().addRewardedIp(plugin.store().hashIp(SurveyManager.ipOf(joined)));
        }
        if (!plugin.getConfig().getBoolean("reminder.on-join", true)) {
            return;
        }
        long delayTicks = Math.max(1L, plugin.getConfig().getLong("reminder.delay-seconds", 5) * 20L);
        java.util.UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline() || survey.hasCompleted(id)) {
                return;
            }
            plugin.core().messages().send(p, "survey-reminder");
        }, delayTicks);
    }
}
