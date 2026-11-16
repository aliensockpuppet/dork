package io.github.aliensockpuppet.dorkmenu.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;
import io.github.aliensockpuppet.dorkmenu.DorkMenuPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class MenuService {
    private static final ClickCallback.Options CLICK = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private static final Pattern NEW_HOME_NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");

    private final DorkMenuPlugin plugin;
    private final DorkCoreApi core;

    public MenuService(DorkMenuPlugin plugin, DorkCoreApi core) {
        this.plugin = plugin;
        this.core = core;
    }

    public static final List<MainEntry> MAIN_ENTRIES = List.of(
            new MainEntry("spawn", "<green>Spawn", true),
            new MainEntry("homes", "<green>Homes", true),
            new MainEntry("rtp", "<green>RTP", true),
            new MainEntry("duel", "<green>RTPQueue", true),
            new MainEntry("stats", "<green>Stats", true),
            new MainEntry("map", "<green>Map", true),
            new MainEntry("store", "<green>Store", true),
            new MainEntry("discord", "<#5865F2>Discord", true),
            new MainEntry("settings", "<green>Settings", false),
            new MainEntry("suicide", "<red>Suicide", true));

    private static boolean installed(String id) {
        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        return switch (id) {
            case "rtp", "duel" -> pm.isPluginEnabled("RTPQueue");
            case "stats" -> pm.isPluginEnabled("DorkStats");
            default -> true;
        };
    }

    public record MainEntry(String id, String defaultLabel, boolean gated) {
    }

    public void openMain(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);
        List<ActionButton> buttons = new ArrayList<>();

        for (MainEntry entry : MAIN_ENTRIES) {
            if (entry.gated() && !enabledAndAllowed(p, c, entry.id())) {
                continue;
            }
            if (!installed(entry.id())) {
                continue;
            }
            buttons.add(mainButton(p, c, entry.id(), entry.defaultLabel()));
        }

        ActionButton close = button(p, c.getString("menu.close-label", "<gray>Close"), null, width, Player::closeDialog);
        show(p, c.getString("menu.title", "<green>Menu"), c.getStringList("menu.body"),
                List.of(), listOrNotice(buttons, close, columns(c, "menu.columns")), null);
    }

    public void dispatch(Player p, String id) {
        if (!core.authGate().isAuthenticated(p)) {
            return;
        }
        FileConfiguration c = plugin.cfg();
        switch (id) {
            case "spawn" -> { if (guard(p, c, "spawn")) runSpawn(p); }
            case "homes" -> { if (guard(p, c, "homes")) openHomes(p); }
            case "duel" -> { if (guard(p, c, "duel")) openDuel(p); }
            case "rtp" -> { if (guard(p, c, "rtp")) runRtp(p); }
            case "stats" -> { if (guard(p, c, "stats")) openStats(p); }
            case "map" -> { if (guard(p, c, "map")) sendMap(p); }
            case "store" -> { if (guard(p, c, "store")) sendStore(p); }
            case "settings" -> openSettings(p);
            case "suicide" -> { if (guard(p, c, "suicide")) openSuicideConfirm(p); }
            case "discord" -> {
                if (c.getBoolean("buttons.discord.enabled", true)
                        && allowed(p, c.getString("buttons.discord.permission"))) {
                    sendDiscord(p);
                }
            }
            case "menu", "main" -> openMain(p);
            default -> {
            }
        }
    }

    private boolean guard(Player p, FileConfiguration c, String key) {
        if (enabledAndAllowed(p, c, key)) {
            return true;
        }
        deny(p);
        return false;
    }

    private boolean enabledAndAllowed(Player p, FileConfiguration c, String key) {
        return c.getBoolean("buttons." + key + ".enabled", true)
                && allowed(p, c.getString("buttons." + key + ".permission"));
    }

    private ActionButton mainButton(Player p, FileConfiguration c, String key, String defLabel) {
        String label = orDefault(c.getString("buttons." + key + ".label"), defLabel);
        String tooltip = c.getString("buttons." + key + ".tooltip");
        String iconName = c.getString("buttons." + key + ".icon");
        int w = width(c);
        ActionButton.Builder b = ActionButton.builder(render(withIcon(label, iconName)))
                .width(w)
                .action(DialogAction.customClick(
                        net.kyori.adventure.key.Key.key("dorkmenu", key),
                        (net.kyori.adventure.nbt.api.BinaryTagHolder) null));
        if (tooltip != null && !tooltip.isBlank()) {
            b.tooltip(render(tooltip));
        }
        return b.build();
    }

    private record ToggleRow(String inputKey, String configKey, String defName, PlayerToggles.Toggle toggle) {
    }

    private static final List<ToggleRow> TOGGLE_ROWS = List.of(
            new ToggleRow("tpa_requests", "tpa-requests", "Teleport requests", PlayerToggles.Toggle.TPA_REQUESTS),
            new ToggleRow("tpa_auto", "tpa-auto", "Auto-accept all teleports", PlayerToggles.Toggle.TPA_AUTO),
            new ToggleRow("chat", "chat", "Public chat", PlayerToggles.Toggle.CHAT),
            new ToggleRow("receive_pms", "receive-pms", "Private messages", PlayerToggles.Toggle.RECEIVE_PMS),
            new ToggleRow("join_leave", "join-leave", "Join and leave messages", PlayerToggles.Toggle.JOIN_LEAVE_MESSAGES),
            new ToggleRow("death_messages", "death-messages", "Death messages", PlayerToggles.Toggle.DEATH_MESSAGES),
            new ToggleRow("queue_alerts", "queue-alerts", "Queue alerts", PlayerToggles.Toggle.QUEUE_ALERTS),
            new ToggleRow("phantom_spawns", "phantom-spawns", "Phantom spawns", PlayerToggles.Toggle.PHANTOM_SPAWNS),
            new ToggleRow("message_style", "message-style", "Modern messages and sounds", PlayerToggles.Toggle.MESSAGE_STYLE));

    public void openSettings(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);
        UUID id = p.getUniqueId();
        PlayerToggles toggles = core.playerToggles();

        List<DialogInput> inputs = new ArrayList<>();
        for (ToggleRow row : TOGGLE_ROWS) {
            String label = orDefault(c.getString("buttons.toggle-" + row.configKey() + ".name"), row.defName());
            inputs.add(DialogInput.bool(row.inputKey(), render("<gray>" + label))
                    .initial(toggles.isOn(id, row.toggle()))
                    .build());
        }

        ActionButton save = button(p, c.getString("settings-menu.save-label", "<green>Save"), null, width,
                (player, view) -> saveSettings(player, view));
        ActionButton reset = button(p, c.getString("settings-menu.reset-label", "<red>Reset to defaults"),
                c.getString("settings-menu.reset-tooltip", "<gray>Put every toggle back to its default"),
                width, this::resetSettings);
        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openMain);
        show(p, c.getString("settings-menu.title", "<green>Settings"),
                c.getStringList("settings-menu.body"), inputs,
                DialogType.multiAction(List.of(save, reset)).exitAction(back).columns(1).build(), null);
    }

    private void resetSettings(Player p) {
        UUID id = p.getUniqueId();
        PlayerToggles toggles = core.playerToggles();
        boolean anyChange = false;
        for (ToggleRow row : TOGGLE_ROWS) {
            anyChange |= toggles.isOn(id, row.toggle()) != row.toggle().defaultOn();
        }
        if (anyChange && onToggleCooldown(p)) {
            return;
        }
        for (ToggleRow row : TOGGLE_ROWS) {
            boolean def = row.toggle().defaultOn();
            if (toggles.isOn(id, row.toggle()) != def) {
                toggles.set(id, row.toggle(), def);
            }
        }
        if (anyChange) {
            startToggleCooldown(p);
        }
        core.messages().send(p, "settings-reset");
        core.sounds().play(p, "toggle-off");
        openSettings(p);
    }

    private void saveSettings(Player p, DialogResponseView view) {
        if (view == null) {
            openMain(p);
            return;
        }
        UUID id = p.getUniqueId();
        PlayerToggles toggles = core.playerToggles();

        boolean anyChange = false;
        for (ToggleRow row : TOGGLE_ROWS) {
            boolean current = toggles.isOn(id, row.toggle());
            anyChange |= readBool(view, row.inputKey(), current) != current;
        }
        if (anyChange && onToggleCooldown(p)) {
            return;
        }

        int changed = 0;
        for (ToggleRow row : TOGGLE_ROWS) {
            boolean current = toggles.isOn(id, row.toggle());
            boolean next = readBool(view, row.inputKey(), current);
            if (next != current) {
                toggles.set(id, row.toggle(), next);
                core.messages().send(p, next ? row.configKey() + "-on" : row.configKey() + "-off");
                changed++;
            }
        }
        if (changed > 0) {
            startToggleCooldown(p);
            core.sounds().play(p, "toggle-on");
        }
        core.messages().send(p, "settings-saved");
        p.closeDialog();
    }

    private boolean onToggleCooldown(Player p) {
        long left = core.cooldowns().remaining(p.getUniqueId(), "toggle");
        if (left <= 0L) {
            return false;
        }
        core.messages().send(p, "cooldown", "%seconds%", String.valueOf((left + 999L) / 1000L));
        core.sounds().play(p, "error");
        p.closeDialog();
        return true;
    }

    private void startToggleCooldown(Player p) {
        long rolled = core.cooldowns().roll("toggle", 3L);
        long floor = java.util.concurrent.ThreadLocalRandom.current().nextLong(2_000L, 4_001L);
        core.cooldowns().start(p.getUniqueId(), "toggle", Math.max(rolled, floor));
    }

    private boolean readBool(DialogResponseView view, String key, boolean fallback) {
        Boolean value = view.getBoolean(key);
        return value == null ? fallback : value;
    }

    public void openDuel(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);

        io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi queue = queueApi();
        if (queue == null) {
            List<String> lines = List.of(c.getString("duel-menu.unavailable", "<red>Matchmaking is not available right now"));
            ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openMain);
            show(p, c.getString("duel-menu.title", "<green>Queue"), lines, List.of(),
                    DialogType.notice(back), c.getString("buttons.duel.icon", "IRON_SWORD"));
            return;
        }

        String queuedMode = queue.queuedMode(p.getUniqueId()).orElse(null);
        List<ActionButton> buttons = new ArrayList<>();
        for (io.github.aliensockpuppet.rtpqueue.api.QueueMode mode : queue.modes()) {
            boolean inThisMode = mode.id().equals(queuedMode);
            String labelKey = inThisMode ? "duel-menu.mode-label-queued" : "duel-menu.mode-label";
            String tooltipKey = inThisMode ? "duel-menu.mode-tooltip-queued" : "duel-menu.mode-tooltip";
            String label = c.getString(labelKey, inThisMode ? "<green><name> (in queue)" : "<gray>Join <name>")
                    .replace("<name>", mode.name());
            String tooltip = c.getString(tooltipKey,
                            inThisMode ? "<gray>Tap to leave this queue" : "<gray>Tap to join")
                    .replace("<name>", mode.name());
            String modeId = mode.id();
            buttons.add(iconButton(p, c, "duel-mode", label, tooltip, width, pl -> {
                pl.performCommand("rtpqueue:rtpqueue " + modeId);
                reopenDuelSoon(pl.getUniqueId());
            }));
        }

        buttons.add(button(p,
                c.getString("queues-menu.open-label", "<item:player_head> <green>Who's queued"),
                c.getString("queues-menu.open-tooltip", "<gray>See everyone waiting, by queue"),
                width, this::openQueues));

        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openMain);
        show(p, c.getString("duel-menu.title", "<green>Queue"), c.getStringList("duel-menu.body"), List.of(),
                listOrNotice(buttons, back, columns(c, "duel-menu.columns")),
                c.getString("buttons.duel.icon", "IRON_SWORD"));
    }

    public void openQueues(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);
        io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi queue = queueApi();

        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openDuel);
        if (queue == null) {
            List<String> lines = List.of(c.getString("duel-menu.unavailable",
                    "<red>Matchmaking is not available right now"));
            show(p, c.getString("queues-menu.title", "<green>Queues"), lines, List.of(),
                    DialogType.notice(back), c.getString("buttons.duel.icon", "IRON_SWORD"));
            return;
        }

        List<String> body = new ArrayList<>();
        for (io.github.aliensockpuppet.rtpqueue.api.QueueMode mode : queue.modes()) {
            List<String> waiting = queue.queuedNames(mode.id());
            body.add(c.getString("queues-menu.mode-header", "<green><name> <gray>(<count> waiting)")
                    .replace("<name>", mode.name())
                    .replace("<count>", String.valueOf(waiting.size())));
            if (waiting.isEmpty()) {
                body.add(c.getString("queues-menu.empty-line", "<dark_gray>  nobody waiting"));
            } else {
                int cap = 20;
                int shown = Math.min(cap, waiting.size());
                for (int i = 0; i < shown; i++) {
                    String name = waiting.get(i);
                    body.add("<head:" + name + "> <gray>" + name);
                }
                if (waiting.size() > shown) {
                    body.add("<dark_gray>  + " + (waiting.size() - shown) + " more");
                }
            }
        }

        ActionButton refresh = button(p,
                c.getString("queues-menu.refresh-label", "<green>Refresh"),
                c.getString("queues-menu.refresh-tooltip", "<gray>Reload the list"),
                width, this::openQueues);
        show(p, c.getString("queues-menu.title", "<green>Queues"), body, List.of(),
                DialogType.multiAction(List.of(refresh)).exitAction(back).columns(1).build(),
                c.getString("buttons.duel.icon", "IRON_SWORD"));
    }

    private void reopenDuelSoon(UUID playerId) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null || !p.isOnline()) {
                return;
            }
            io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi queue = queueApi();
            if (queue == null || queue.isProcessing(playerId)) {
                return;
            }
            openDuel(p);
        }, 2L);
    }

    private io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi queueApi() {
        try {
            org.bukkit.plugin.RegisteredServiceProvider<io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi> rsp =
                    Bukkit.getServicesManager().getRegistration(io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi.class);
            return rsp == null ? null : rsp.getProvider();
        } catch (NoClassDefFoundError | RuntimeException e) {
            return null;
        }
    }

    public void runRtp(Player p) {
        FileConfiguration c = plugin.cfg();
        if (!allowed(p, c.getString("buttons.rtp.permission"))) {
            deny(p);
            openMain(p);
            return;
        }
        p.closeDialog();
        p.performCommand("rtpqueue:rtp");
    }

    public void openSuicideConfirm(Player p) {
        FileConfiguration c = plugin.cfg();
        if (!allowed(p, c.getString("buttons.suicide.permission"))) {
            deny(p);
            openMain(p);
            return;
        }
        int width = width(c);
        ActionButton yes = button(p, c.getString("suicide-menu.confirm-label", "<red>Yes, kill me"), null, width, pl -> {
            pl.closeDialog();
            pl.performCommand("dorkcore:suicide");
        });
        ActionButton no = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openMain);
        show(p, c.getString("suicide-menu.title", "<red>Suicide"),
                c.getStringList("suicide-menu.body"), List.of(),
                DialogType.multiAction(List.of(yes)).exitAction(no).columns(1).build(),
                c.getString("suicide-menu.icon", "SKELETON_SKULL"));
    }

    private void openLeaderboard(Player p, io.github.aliensockpuppet.dorkstats.api.StatType type) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);
        int limit = Math.max(1, Math.min(25, c.getInt("leaderboards-menu.rows", 10)));
        boolean heads = c.getBoolean("leaderboards-menu.show-heads", true);
        String rowFormat = c.getString("leaderboards-menu.row", "<gray>%rank%. <green>%name% <gray>%value%");

        List<DialogBody> body = new ArrayList<>();
        io.github.aliensockpuppet.dorkstats.api.DorkStatsApi stats = statsApi();
        if (stats == null) {
            body.add(DialogBody.plainMessage(render(c.getString("leaderboards-menu.unavailable",
                    "<red>Leaderboards are not available right now"))));
        } else {
            List<io.github.aliensockpuppet.dorkstats.api.StatEntry> rows = stats.top(type, limit);
            if (rows.isEmpty()) {
                body.add(DialogBody.plainMessage(render(c.getString("leaderboards-menu.empty", "<gray>No entries yet"))));
            } else {
                int rank = 1;
                for (io.github.aliensockpuppet.dorkstats.api.StatEntry entry : rows) {
                    String line = rowFormat
                            .replace("%rank%", Integer.toString(rank))
                            .replace("%name%", entry.name())
                            .replace("%value%", type.format(entry.value()));
                    body.add(leaderboardRow(entry.name(), line, heads));
                    rank++;
                }
            }
        }

        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openStats);
        showBodies(p, c.getString("leaderboards-menu.list-title", "<green>Top <name>").replace("<name>", type.display()),
                body, List.of(), DialogType.notice(back));
    }

    private void openKdrLeaderboard(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);
        int limit = Math.max(1, Math.min(25, c.getInt("leaderboards-menu.rows", 10)));
        boolean heads = c.getBoolean("leaderboards-menu.show-heads", true);
        String rowFormat = c.getString("leaderboards-menu.kdr-row",
                "<gray>%rank%. <green>%name% <gray>%kdr% <gray>(%kills%/%deaths%)");

        List<DialogBody> body = new ArrayList<>();
        io.github.aliensockpuppet.dorkstats.api.DorkStatsApi stats = statsApi();
        if (stats == null) {
            body.add(DialogBody.plainMessage(render(c.getString("leaderboards-menu.unavailable",
                    "<red>Leaderboards are not available right now"))));
        } else {
            List<io.github.aliensockpuppet.dorkstats.api.KdrEntry> rows = stats.topKdr(limit);
            if (rows.isEmpty()) {
                body.add(DialogBody.plainMessage(render(c.getString("leaderboards-menu.empty", "<gray>No entries yet"))));
            } else {
                int rank = 1;
                for (io.github.aliensockpuppet.dorkstats.api.KdrEntry entry : rows) {
                    String line = rowFormat
                            .replace("%rank%", Integer.toString(rank))
                            .replace("%name%", entry.name())
                            .replace("%kdr%", String.format(Locale.ROOT, "%.2f", entry.kdr()))
                            .replace("%kills%", Long.toString(entry.kills()))
                            .replace("%deaths%", Long.toString(entry.deaths()));
                    body.add(leaderboardRow(entry.name(), line, heads));
                    rank++;
                }
            }
        }
        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openStats);
        showBodies(p, c.getString("leaderboards-menu.kdr-title", "<green>Top players"),
                body, List.of(), DialogType.notice(back));
    }

    private DialogBody leaderboardRow(String playerName, String line, boolean heads) {
        if (heads) {
            try {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                    head.setItemMeta(meta);
                    return DialogBody.item(head)
                            .description(DialogBody.plainMessage(render(line)))
                            .showTooltip(false)
                            .build();
                }
            } catch (RuntimeException ignored) {
            }
        }
        return DialogBody.plainMessage(render(line));
    }

    public void sendDiscord(Player p) {
        FileConfiguration c = plugin.cfg();
        String url = c.getString("discord.url", "");
        if (url == null || url.isBlank()) {
            p.closeDialog();
            p.sendMessage(core.colors().render("<gray>This link is not set up yet"));
            core.sounds().play(p, "error");
            return;
        }
        String text = c.getString("discord.message", "<green>Join our Discord: <click:open_url:'<url>'><underlined><url></underlined></click>")
                .replace("<url>", url);
        p.closeDialog();
        p.sendMessage(core.colors().render(text));
        core.sounds().play(p, "menu-open");
    }

    public void sendMap(Player p) {
        FileConfiguration c = plugin.cfg();
        String url = c.getString("map.url", "");
        if (url == null || url.isBlank()) {
            p.closeDialog();
            p.sendMessage(core.colors().render("<gray>This link is not set up yet"));
            core.sounds().play(p, "error");
            return;
        }
        String text = c.getString("map.message", "<green>Live map: <click:open_url:'<url>'><underlined><url></underlined></click>")
                .replace("<url>", url);
        p.closeDialog();
        p.sendMessage(core.colors().render(text));
        core.sounds().play(p, "menu-open");
    }

    public void sendStore(Player p) {
        FileConfiguration c = plugin.cfg();
        String url = c.getString("store.url", "");
        if (url == null || url.isBlank()) {
            p.closeDialog();
            p.sendMessage(core.colors().render("<gray>This link is not set up yet"));
            core.sounds().play(p, "error");
            return;
        }
        String text = c.getString("store.message", "<green>Our store: <click:open_url:'<url>'><underlined><url></underlined></click>")
                .replace("<url>", url);
        p.closeDialog();
        p.sendMessage(core.colors().render(text));
        core.sounds().play(p, "menu-open");
    }

    public void openStats(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);

        io.github.aliensockpuppet.dorkstats.api.DorkStatsApi stats = statsApi();
        List<String> lines = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openMain);

        if (stats == null) {
            lines.add(c.getString("stats-menu.unavailable", "<red>Stats are not available right now"));
            show(p, c.getString("stats-menu.title", "<green>Your stats"), lines, List.of(),
                    DialogType.notice(back), c.getString("stats-menu.icon", "DIAMOND_SWORD"));
            return;
        }

        UUID id = p.getUniqueId();
        long kills = stats.value(id, io.github.aliensockpuppet.dorkstats.api.StatType.KILLS);
        long deaths = stats.value(id, io.github.aliensockpuppet.dorkstats.api.StatType.DEATHS);
        long placed = stats.value(id, io.github.aliensockpuppet.dorkstats.api.StatType.PLACED);
        long broken = stats.value(id, io.github.aliensockpuppet.dorkstats.api.StatType.BROKEN);
        long playtime = stats.value(id, io.github.aliensockpuppet.dorkstats.api.StatType.PLAYTIME);
        double kdr = stats.kdr(id);
        lines.add(c.getString("stats-menu.kills", "<gray>Kills: <green>%v%").replace("%v%", Long.toString(kills)));
        lines.add(c.getString("stats-menu.deaths", "<gray>Deaths: <green>%v%").replace("%v%", Long.toString(deaths)));
        lines.add(c.getString("stats-menu.kdr", "<gray>K/D: <green>%v%")
                .replace("%v%", String.format(Locale.ROOT, "%.2f", kdr)));
        lines.add(c.getString("stats-menu.placed", "<gray>Blocks placed: <green>%v%").replace("%v%", Long.toString(placed)));
        lines.add(c.getString("stats-menu.broken", "<gray>Blocks broken: <green>%v%").replace("%v%", Long.toString(broken)));
        lines.add(c.getString("stats-menu.playtime", "<gray>Playtime: <green>%v%")
                .replace("%v%", io.github.aliensockpuppet.dorkstats.api.StatType.formatDuration(playtime)));

        buttons.add(button(p, c.getString("stats-menu.kdr-board-label", "<green>Top players (K/D)"),
                null, width, this::openKdrLeaderboard));
        for (io.github.aliensockpuppet.dorkstats.api.StatType type : io.github.aliensockpuppet.dorkstats.api.StatType.values()) {
            buttons.add(button(p, "<green>" + type.display(), null, width, pl -> openLeaderboard(pl, type)));
        }

        List<DialogBody> body = new ArrayList<>();
        DialogBody iconBody = itemIcon(c.getString("stats-menu.icon", "DIAMOND_SWORD"));
        if (iconBody != null) {
            body.add(iconBody);
        }
        addBodyLines(body, lines);
        showBodies(p, c.getString("stats-menu.title", "<green>Your stats"), body, List.of(),
                DialogType.multiAction(buttons).exitAction(back).columns(columns(c, "stats-menu.columns")).build());
    }

    private io.github.aliensockpuppet.dorkstats.api.DorkStatsApi statsApi() {
        try {
            org.bukkit.plugin.RegisteredServiceProvider<io.github.aliensockpuppet.dorkstats.api.DorkStatsApi> rsp =
                    Bukkit.getServicesManager().getRegistration(io.github.aliensockpuppet.dorkstats.api.DorkStatsApi.class);
            return rsp == null ? null : rsp.getProvider();
        } catch (NoClassDefFoundError | RuntimeException e) {
            return null;
        }
    }

    public void runSpawn(Player p) {
        FileConfiguration c = plugin.cfg();
        if (!allowed(p, c.getString("buttons.spawn.permission"))) {
            deny(p);
            openMain(p);
            return;
        }
        p.closeDialog();
        p.performCommand("dorkcore:spawn");
    }

    public void openHomes(Player p) {
        FileConfiguration c = plugin.cfg();
        if (!allowed(p, c.getString("buttons.homes.permission"))) {
            deny(p);
            openMain(p);
            return;
        }
        int width = width(c);
        List<ActionButton> buttons = new ArrayList<>();

        List<String> homeNames = core.homes().names(p);
        for (String name : homeNames) {
            String label = c.getString("homes-menu.home-label", "<green>Home: <name>").replace("<name>", name);
            String tooltip = c.getString("homes-menu.home-tooltip", "<gray>Teleport to <name>").replace("<name>", name);
            buttons.add(iconButton(p, c, "homes-go", label, tooltip, width, player -> {
                player.closeDialog();
                player.performCommand("dorkcore:home " + name);
            }));
        }
        buttons.add(iconButton(p, c, "homes-set", c.getString("homes-menu.set-label", "<green>Set a home"),
                c.getString("homes-menu.set-tooltip"), width, this::openSetHome));

        List<String> body = new ArrayList<>(c.getStringList("homes-menu.body"));
        if (homeNames.isEmpty()) {
            body.add(c.getString("homes-menu.none", "<gray>You have no homes yet. Set one below"));
        }

        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openMain);
        show(p, c.getString("homes-menu.title", "<green>Homes"), body,
                List.of(), listOrNotice(buttons, back, columns(c, "homes-menu.columns")),
                c.getString("homes-menu.icon", "WHITE_BED"));
    }

    private void openSetHome(Player p) {
        FileConfiguration c = plugin.cfg();
        int width = width(c);
        DialogInput input = DialogInput.text("home", render(c.getString("homes-menu.set-input-label", "<gray>New home name")))
                .width(Math.max(width, 200))
                .maxLength(16)
                .initial("")
                .build();
        ActionButton save = button(p, c.getString("homes-menu.save-label", "<green>Save"), null, width, (player, view) -> {
            String name = clean(view == null ? null : view.getText("home"));
            if (!NEW_HOME_NAME.matcher(name).matches()) {
                core.messages().send(player, "home-name-invalid");
                core.sounds().play(player, "error");
                openSetHome(player);
                return;
            }
            player.closeDialog();
            player.performCommand("dorkcore:sethome " + name);
        });
        ActionButton back = button(p, c.getString("menu.back-label", "<gray>Back"), null, width, this::openHomes);
        show(p, c.getString("homes-menu.set-title", "<green>Set a home"),
                c.getStringList("homes-menu.set-body"), List.of(input),
                DialogType.multiAction(List.of(save)).exitAction(back).columns(1).build(),
                c.getString("homes-menu.icon", "WHITE_BED"));
    }

    private DialogType listOrNotice(List<ActionButton> buttons, ActionButton exit, int columns) {
        if (buttons.isEmpty()) {
            return DialogType.notice(exit);
        }
        return DialogType.multiAction(buttons).exitAction(exit).columns(columns).build();
    }

    private void show(Player p, String title, List<String> bodyLines, List<DialogInput> inputs,
                      DialogType type, String icon) {
        List<DialogBody> body = new ArrayList<>();
        DialogBody iconBody = itemIcon(icon);
        if (iconBody != null) {
            body.add(iconBody);
        }
        addBodyLines(body, bodyLines);
        showDialog(p, title, body, inputs, type);
    }

    private void showBodies(Player p, String title, List<DialogBody> body, List<DialogInput> inputs, DialogType type) {
        showDialog(p, title, body, inputs, type);
    }

    private void addBodyLines(List<DialogBody> body, List<String> bodyLines) {
        for (String line : bodyLines) {
            if (line != null && !line.isEmpty()) {
                body.add(DialogBody.plainMessage(render(line)));
            }
        }
    }

    private void showDialog(Player p, String title, List<DialogBody> body,
                            List<DialogInput> inputs, DialogType type) {
        DialogBase base = DialogBase.builder(render(title == null ? "" : title))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .inputs(inputs)
                .build();
        Dialog dialog = Dialog.create(builder -> builder.empty().base(base).type(type));
        p.showDialog(dialog);
    }

    private DialogBody itemIcon(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(icon.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }
        try {
            return DialogBody.item(ItemStack.of(material)).showTooltip(false).build();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ActionButton iconButton(Player owner, FileConfiguration c, String key, String label,
                                    String tooltip, int width, Consumer<Player> action) {
        return iconButton(owner, c, key, label, tooltip, width, (player, view) -> action.accept(player));
    }

    private ActionButton iconButton(Player owner, FileConfiguration c, String key, String label,
                                    String tooltip, int width, BiConsumer<Player, DialogResponseView> action) {
        String iconName = c.getString("buttons." + key + ".icon");
        return button(owner, label, tooltip, width, iconName, action);
    }

    private ActionButton button(Player owner, String label, String tooltip, int width, Consumer<Player> action) {
        return button(owner, label, tooltip, width, null, (player, view) -> action.accept(player));
    }

    private ActionButton button(Player owner, String label, String tooltip, int width,
                                BiConsumer<Player, DialogResponseView> action) {
        return button(owner, label, tooltip, width, null, action);
    }

    private ActionButton button(Player owner, String label, String tooltip, int width, String iconName,
                                BiConsumer<Player, DialogResponseView> action) {
        UUID ownerId = owner.getUniqueId();
        ActionButton.Builder b = ActionButton.builder(render(withIcon(label, iconName)))
                .width(width)
                .action(DialogAction.customClick((view, audience) -> handle(ownerId, audience, view, action), CLICK));
        if (tooltip != null && !tooltip.isBlank()) {
            b.tooltip(render(tooltip));
        }
        return b.build();
    }

    private String withIcon(String label, String iconName) {
        String text = label == null ? "" : label;
        if (iconName == null || iconName.isBlank()) {
            return text;
        }
        return "<gray>" + iconName + "</gray> " + text;
    }

    private void handle(UUID ownerId, Audience audience, DialogResponseView view,
                        BiConsumer<Player, DialogResponseView> action) {
        if (!(audience instanceof Player clicker) || !clicker.getUniqueId().equals(ownerId)) {
            return;
        }
        Runnable task = () -> {
            Player p = Bukkit.getPlayer(ownerId);
            if (p == null || !p.isOnline()) {
                return;
            }
            try {
                action.accept(p, view);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Menu action failed for " + p.getName(), e);
                p.closeDialog();
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    private Component render(String miniMessage) {
        return core.colors().render(miniMessage == null ? "" : miniMessage);
    }

    private boolean allowed(Player p, String permission) {
        return permission == null || permission.isBlank() || p.hasPermission(permission);
    }

    private void deny(Player p) {
        core.messages().send(p, "no-permission");
        core.sounds().play(p, "error");
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("<", "").replace(">", "").replace("&", "").replace("§", "").trim();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private static int width(FileConfiguration c) {
        return Math.max(1, Math.min(1024, c.getInt("menu.button-width", 200)));
    }

    private static int columns(FileConfiguration c, String path) {
        return Math.max(1, Math.min(8, c.getInt(path, 1)));
    }
}
