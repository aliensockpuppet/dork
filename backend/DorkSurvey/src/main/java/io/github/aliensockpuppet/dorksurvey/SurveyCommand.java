package io.github.aliensockpuppet.dorksurvey;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.util.List;

public final class SurveyCommand implements TabExecutor {
    private final DorkSurveyPlugin plugin;
    private final DorkCoreApi core;
    private final SurveyManager survey;

    public SurveyCommand(DorkSurveyPlugin plugin, DorkCoreApi core, SurveyManager survey) {
        this.plugin = plugin;
        this.core = core;
        this.survey = survey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dorksurvey.admin")) {
                core.messages().send(sender, "no-permission");
                return true;
            }
            plugin.reloadFromDisk();
            core.messages().send(sender, "survey-reloaded");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("dorksurvey.admin")) {
                core.messages().send(sender, "no-permission");
                return true;
            }
            if (args.length < 2) {
                core.messages().send(sender, "survey-reset-usage");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[1]);
            }
            plugin.store().reset(target.getUniqueId());
            core.messages().send(sender, "survey-reset-done", "%player%", args[1]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorksurvey.take")) {
            core.messages().send(player, "no-permission");
            core.sounds().play(player, "error");
            return true;
        }
        survey.openNext(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("dorksurvey.admin")) {
            List<String> out = new java.util.ArrayList<>();
            for (String sub : List.of("reset", "reload")) {
                if (sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("dorksurvey.admin")) {
            List<String> out = new java.util.ArrayList<>();
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
