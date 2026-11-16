package io.github.aliensockpuppet.dorkmotd.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.Locale;

public final class MotdCommand implements SimpleCommand {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DorkMotdVelocity plugin;
    private final MotdManager motd;

    public MotdCommand(DorkMotdVelocity plugin, MotdManager motd) {
        this.plugin = plugin;
        this.motd = motd;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dorkmotd.admin")) {
                send(sender, "no-permission");
                return;
            }
            plugin.reloadFromDisk();
            send(sender, "motd-reloaded");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("dorkmotd.admin")) {
                send(sender, "no-permission");
                return;
            }
            plugin.store().clearMotd();
            motd.rebuildCache();
            send(sender, "motd-reset");
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return;
        }
        if (plugin.requirePermission() && !player.hasPermission("dorkmotd.set")) {
            send(player, "no-permission");
            return;
        }
        if (args.length == 0) {
            send(player, "usage");
            return;
        }
        apply(player, String.join(" ", args));
    }

    private void apply(Player player, String rawInput) {
        long now = System.currentTimeMillis();
        MotdManager.Result result = motd.attemptSet(player, rawInput, now);
        switch (result) {
            case OK -> send(player, "motd-set");
            case GLOBAL_COOLDOWN -> send(player, "motd-global-cooldown",
                    "%seconds%", String.valueOf(motd.globalCooldownLeft(now)));
            case PLAYER_COOLDOWN -> send(player, "motd-player-cooldown",
                    "%seconds%", String.valueOf(motd.playerCooldownLeft(player.getUniqueId(), now)));
            case INVALID -> send(player, "motd-invalid");
            case EMPTY -> send(player, "motd-empty");
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return !(invocation.source() instanceof Player);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1 && invocation.source().hasPermission("dorkmotd.admin")) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> out = new java.util.ArrayList<>();
            for (String sub : List.of("reset", "reload")) {
                if (sub.startsWith(partial)) {
                    out.add(sub);
                }
            }
            return out;
        }
        return List.of();
    }

    private void send(CommandSource to, String key, String... replacements) {
        String raw = plugin.message(key);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        to.sendMessage(MINI.deserialize(raw));
    }
}
