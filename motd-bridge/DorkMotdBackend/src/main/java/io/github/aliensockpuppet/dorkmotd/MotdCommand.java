package io.github.aliensockpuppet.dorkmotd;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.util.List;

public final class MotdCommand implements TabExecutor {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final ClickCallback.Options CLICK = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(java.time.Duration.ofMinutes(5))
            .build();

    private final DorkMotdPlugin plugin;
    private final DorkCoreApi core;
    private final ProxyBridge bridge;

    public MotdCommand(DorkMotdPlugin plugin, DorkCoreApi core, ProxyBridge bridge) {
        this.plugin = plugin;
        this.core = core;
        this.bridge = bridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dorkmotd.admin")) {
                core.messages().send(sender, "no-permission");
                return true;
            }
            plugin.reloadFromDisk();
            core.messages().send(sender, "motd-reloaded");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("dorkmotd.admin")) {
                core.messages().send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player player)) {
                core.messages().send(sender, "players-only");
                return true;
            }
            if (!bridge.sendSet(player, "\u0000reset")) {
                proxyDown(player);
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "players-only");
            return true;
        }
        if (plugin.getConfig().getBoolean("motd.require-permission", false)
                && !player.hasPermission("dorkmotd.set")) {
            core.messages().send(player, "no-permission");
            core.sounds().play(player, "error");
            return true;
        }
        if (args.length == 0) {
            openDialog(player);
            return true;
        }
        forward(player, String.join(" ", args));
        return true;
    }

    private void forward(Player player, String rawInput) {
        if (!bridge.sendSet(player, rawInput)) {
            proxyDown(player);
        }
    }

    private void proxyDown(Player player) {
        if (core.messages().has("motd-proxy-down")) {
            core.messages().send(player, "motd-proxy-down");
        } else {
            player.sendMessage(render("<red>The MOTD service is unavailable right now, try again soon"));
        }
        core.sounds().play(player, "error");
    }

    private void openDialog(Player player) {
        String title = plugin.getConfig().getString("dialog.title", "<green>Set the server MOTD");

        List<DialogBody> body = new java.util.ArrayList<>();
        for (String line : plugin.getConfig().getStringList("dialog.body")) {
            if (line != null && !line.isEmpty()) {
                body.add(DialogBody.plainMessage(render(line)));
            }
        }

        DialogInput input = DialogInput.text("motd",
                        render(plugin.getConfig().getString("dialog.input-label", "<gray>New MOTD")))
                .width(300)
                .maxLength(Math.max(1, plugin.getConfig().getInt("motd.max-length", 100)))
                .initial("")
                .build();

        DialogBase base = DialogBase.builder(render(title))
                .canCloseWithEscape(true)
                .body(body)
                .inputs(List.of(input))
                .build();

        ActionButton set = ActionButton.builder(render(plugin.getConfig().getString("dialog.set-label", "<green>Set")))
                .width(200)
                .action(DialogAction.customClick((view, audience) -> onDialogSet(player.getUniqueId(), view), CLICK))
                .build();
        ActionButton cancel = ActionButton.builder(render(plugin.getConfig().getString("dialog.cancel-label", "<red>Cancel")))
                .width(200)
                .build();

        Dialog dialog = Dialog.create(f -> f.empty().base(base)
                .type(DialogType.confirmation(set, cancel)));
        player.showDialog(dialog);
    }

    private void onDialogSet(java.util.UUID playerId, DialogResponseView view) {
        String text = view == null ? null : view.getText("motd");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p == null || !p.isOnline()) {
                return;
            }
            p.closeDialog();
            if (text == null || text.isBlank()) {
                core.messages().send(p, "motd-empty");
                core.sounds().play(p, "error");
                return;
            }
            forward(p, text);
        });
    }

    private Component render(String miniMessage) {
        return MINI.deserialize(miniMessage == null ? "" : miniMessage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("dorkmotd.admin")) {
            List<String> out = new java.util.ArrayList<>();
            for (String sub : List.of("reset", "reload")) {
                if (sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        return List.of();
    }
}
