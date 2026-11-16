package io.github.aliensockpuppet.rtpqueue.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;
import io.github.aliensockpuppet.rtpqueue.config.RtpSettings;
import io.github.aliensockpuppet.rtpqueue.config.Settings;
import io.github.aliensockpuppet.rtpqueue.rtp.LocationFinder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class RtpCommand implements TabExecutor {
    private static final long DEFAULT_RTP_COOLDOWN_SECONDS = 10L;
    private static final long FLOOR_MIN_MILLIS = 5_000L;
    private static final long FLOOR_MAX_MILLIS = 10_000L;

    private final RTPQueuePlugin plugin;
    private final LocationFinder finder;
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public RtpCommand(RTPQueuePlugin plugin) {
        this.plugin = plugin;
        this.finder = new LocationFinder(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.core().messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("rtpqueue.rtp")) {
            plugin.core().messages().send(player, "no-permission");
            plugin.core().sounds().play(player, "error");
            return true;
        }
        if (!plugin.core().authGate().isAuthenticated(player)) {
            plugin.core().messages().send(player, "not-authenticated");
            plugin.core().sounds().play(player, "error");
            return true;
        }
        Settings s = plugin.settings();
        if (!s.worldAllowed(player.getWorld())) {
            plugin.core().messages().send(player, "world-not-allowed");
            plugin.core().sounds().play(player, "error");
            return true;
        }
        if (plugin.core().combatTag().isTagged(player)) {
            long seconds = (plugin.core().combatTag().remainingMillis(player) + 999L) / 1000L;
            plugin.core().messages().send(player, "combat-tagged", "%seconds%", String.valueOf(seconds));
            plugin.core().sounds().play(player, "error");
            return true;
        }
        UUID id = player.getUniqueId();
        long left = plugin.core().cooldowns().remaining(id, "rtp");
        if (left > 0L) {
            long seconds = (left + 999L) / 1000L;
            plugin.core().messages().send(player, "cooldown", "%seconds%", String.valueOf(seconds));
            plugin.core().sounds().play(player, "error");
            return true;
        }
        if (!searching.add(id)) {
            return true;
        }

        World world = Bukkit.getWorld(s.rtp().world());
        if (world == null) {
            searching.remove(id);
            plugin.getLogger().warning("rtp.world '" + s.rtp().world() + "' is not loaded. /rtp is off until it is.");
            plugin.core().messages().send(player, "rtp-failed");
            plugin.core().sounds().play(player, "error");
            return true;
        }

        long rolled = plugin.core().cooldowns().roll("rtp", DEFAULT_RTP_COOLDOWN_SECONDS);
        long floor = java.util.concurrent.ThreadLocalRandom.current().nextLong(FLOOR_MIN_MILLIS, FLOOR_MAX_MILLIS + 1L);
        long millis = Math.max(rolled, floor);
        plugin.core().cooldowns().start(id, "rtp", millis);
        plugin.core().messages().send(player, "rtp-searching");

        RtpSettings rs = s.rtp();
        finder.findRandom(world, rs)
                .whenComplete((spot, error) -> plugin.sync(() -> land(id, spot, error)));
        return true;
    }

    private void land(UUID id, Location spot, Throwable error) {
        searching.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (error != null) {
            plugin.getLogger().log(Level.WARNING, "Solo /rtp spot search failed", error);
        }
        if (spot == null) {
            plugin.core().cooldowns().clear(id, "rtp");
            plugin.core().messages().send(player, "rtp-failed");
            plugin.core().sounds().play(player, "error");
            return;
        }
        if (plugin.core().combatTag().isTagged(player)) {
            long seconds = (plugin.core().combatTag().remainingMillis(player) + 999L) / 1000L;
            plugin.core().messages().send(player, "combat-tagged", "%seconds%", String.valueOf(seconds));
            plugin.core().sounds().play(player, "error");
            return;
        }
        player.teleportAsync(spot, PlayerTeleportEvent.TeleportCause.COMMAND)
                .whenComplete((ok, tpError) -> plugin.sync(() -> {
                    Player now = Bukkit.getPlayer(id);
                    if (now == null || !now.isOnline()) {
                        return;
                    }
                    if (tpError != null || Boolean.FALSE.equals(ok)) {
                        plugin.core().cooldowns().clear(id, "rtp");
                        plugin.core().messages().send(now, "rtp-failed");
                        plugin.core().sounds().play(now, "error");
                        return;
                    }
                    plugin.core().messages().send(now, "rtp-done");
                    plugin.core().sounds().play(now, "rtp-done");
                }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
