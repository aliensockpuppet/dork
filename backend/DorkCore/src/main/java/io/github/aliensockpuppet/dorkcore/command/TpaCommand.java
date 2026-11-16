package io.github.aliensockpuppet.dorkcore.command;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

public final class TpaCommand implements CommandExecutor {
    private final CoreServices services;
    private final Guard guard;
    private final Teleports teleports;
    private final TpaManager tpa;

    public TpaCommand(CoreServices services, Guard guard, Teleports teleports, TpaManager tpa) {
        this.services = services;
        this.guard = guard;
        this.teleports = teleports;
        this.tpa = tpa;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        return switch (command.getName().toLowerCase()) {
            case "tpa" -> request(player, args, TpaManager.Direction.TO_TARGET);
            case "tpahere" -> request(player, args, TpaManager.Direction.FROM_TARGET);
            case "tpaccept" -> accept(player);
            case "tpdeny" -> deny(player);
            case "tpcancel" -> cancel(player);
            default -> true;
        };
    }

    private boolean request(Player sender, String[] args, TpaManager.Direction direction) {
        if (!sender.hasPermission("dorkcore.tpa")) {
            services.messages().send(sender, "no-permission");
            services.sounds().play(sender, "error");
            return true;
        }
        if (args.length < 1) {
            services.messages().send(sender, "tpa-usage");
            return true;
        }
        if (!guard.readyAndOffCooldown(sender, "tpa")) {
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            services.messages().send(sender, "player-not-found", "%player%", strip(args[0]));
            services.sounds().play(sender, "error");
            return true;
        }
        if (target.equals(sender)) {
            services.messages().send(sender, "tpa-self");
            services.sounds().play(sender, "error");
            return true;
        }
        PlayerToggles toggles = services.playerToggles();
        boolean accepting = toggles.isOn(target.getUniqueId(), PlayerToggles.Toggle.TPA_REQUESTS);
        if (!services.authGate().isAuthenticated(target) || !accepting) {
            services.messages().send(sender, "tpa-refused", "%player%", target.getName());
            services.sounds().play(sender, "error");
            return true;
        }

        services.cooldowns().start(sender.getUniqueId(), "tpa",
                services.config().rollCooldownMillis("tpa", services.config().get().tpaCooldownMillis() / 1000L));

        String sentKey = direction == TpaManager.Direction.TO_TARGET ? "tpa-sent" : "tpahere-sent";
        services.messages().send(sender, sentKey, "%player%", target.getName());
        services.sounds().play(sender, "tpa-sent");

        if (toggles.isOn(target.getUniqueId(), PlayerToggles.Toggle.TPA_AUTO)) {
            tpa.open(sender.getUniqueId(), target.getUniqueId(), direction,
                    services.config().get().tpaExpiryMillis());
            services.messages().send(target, "tpa-auto-accepted", "%player%", sender.getName());
            doAccept(target);
            return true;
        }

        tpa.open(sender.getUniqueId(), target.getUniqueId(), direction,
                services.config().get().tpaExpiryMillis());

        String gotKey = direction == TpaManager.Direction.TO_TARGET ? "tpa-received" : "tpahere-received";
        services.messages().send(target, gotKey, "%player%", sender.getName());
        services.sounds().play(target, "tpa-received");
        return true;
    }

    static final Key CONFIRM = Key.key("dorkcore", "tpconfirm");

    private final MiniMessage mini = MiniMessage.miniMessage();

    private void showConfirmDialog(Player target, String senderName, TpaManager.Direction direction) {
        String safe = strip(senderName);
        String line = direction == TpaManager.Direction.TO_TARGET
                ? "<gray>Teleport <green>" + safe + "<gray> to you?"
                : "<gray>Teleport to <green>" + safe + "<gray>?";
        DialogBase base = DialogBase.builder(render("<green>Confirm teleport"))
                .canCloseWithEscape(true)
                .body(java.util.List.of(DialogBody.plainMessage(render(line))))
                .build();
        ActionButton confirm = ActionButton.builder(render("<green>Confirm"))
                .width(200)
                .action(DialogAction.customClick(CONFIRM, (net.kyori.adventure.nbt.api.BinaryTagHolder) null)).build();
        Dialog dialog = Dialog.create(f -> f.empty().base(base)
                .type(DialogType.confirmation(confirm,
                        ActionButton.builder(render("<red>Cancel")).width(200).build())));
        target.showDialog(dialog);
    }

    public void acceptFor(Player target) {
        TpaManager.Request request = tpa.forTarget(target.getUniqueId());
        if (request == null) {
            services.messages().send(target, "tpa-none");
            services.sounds().play(target, "error");
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        String senderName = sender != null ? sender.getName() : "the player";
        showConfirmDialog(target, senderName, request.direction());
    }

    public void confirmFor(Player target) {
        doAccept(target);
    }

    public void denyFor(Player target) {
        deny(target);
    }

    private Component render(String miniMessage) {
        return mini.deserialize(miniMessage);
    }

    private boolean accept(Player target) {
        acceptFor(target);
        return true;
    }

    private boolean doAccept(Player target) {
        TpaManager.Request request = tpa.forTarget(target.getUniqueId());
        if (request == null) {
            services.messages().send(target, "tpa-none");
            services.sounds().play(target, "error");
            return true;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            tpa.clearTarget(target.getUniqueId());
            services.messages().send(target, "tpa-sender-gone");
            services.sounds().play(target, "error");
            return true;
        }

        Player mover = request.direction() == TpaManager.Direction.TO_TARGET ? sender : target;
        Player anchor = mover == sender ? target : sender;

        if (!guard.ready(mover)) {
            tpa.clearTarget(target.getUniqueId());
            services.messages().send(anchor, "tpa-cancelled-gate");
            return true;
        }

        tpa.clearTarget(target.getUniqueId());
        services.messages().send(sender, "tpa-accepted", "%player%", target.getName());
        services.messages().send(target, "tpa-accepted-you", "%player%", sender.getName());
        teleports.teleport(mover, anchor.getLocation(), "tpa-move",
                0L, "tpa-teleported", "teleport", "%player%", anchor.getName());
        return true;
    }

    private boolean deny(Player target) {
        TpaManager.Request request = tpa.clearTarget(target.getUniqueId());
        if (request == null) {
            services.messages().send(target, "tpa-none");
            services.sounds().play(target, "error");
            return true;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        services.messages().send(target, "tpa-denied");
        if (sender != null && sender.isOnline()) {
            services.messages().send(sender, "tpa-denied-by", "%player%", target.getName());
            services.sounds().play(sender, "error");
        }
        return true;
    }

    private boolean cancel(Player sender) {
        TpaManager.Request request = tpa.fromSender(sender.getUniqueId());
        if (request == null) {
            services.messages().send(sender, "tpa-none-sent");
            services.sounds().play(sender, "error");
            return true;
        }
        tpa.clearSender(sender.getUniqueId());
        Player target = Bukkit.getPlayer(request.target());
        services.messages().send(sender, "tpa-cancelled");
        if (target != null && target.isOnline()) {
            services.messages().send(target, "tpa-cancelled-by", "%player%", sender.getName());
        }
        return true;
    }

    private static String strip(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }
}
