package io.github.aliensockpuppet.rtpqueue.queue;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;
import io.github.aliensockpuppet.rtpqueue.api.QueueMode;
import io.github.aliensockpuppet.rtpqueue.config.Settings;
import io.github.aliensockpuppet.rtpqueue.gear.GearProfile;
import io.github.aliensockpuppet.rtpqueue.gear.GearScanner;
import io.github.aliensockpuppet.rtpqueue.rtp.LocationFinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class QueueManager {
    public enum Result { JOINED, SWITCHED, LEFT, BUSY }

    private record CachedProfile(GearProfile profile, int tick) {
    }

    private final RTPQueuePlugin plugin;
    private final GearScanner scanner;
    private final LocationFinder finder;

    private final Map<String, ArrayDeque<UUID>> queues = new HashMap<>();
    private final Map<UUID, String> queuedIn = new HashMap<>();
    private final Set<UUID> processing = new HashSet<>();
    private final Map<UUID, CachedProfile> profiles = new HashMap<>();
    private final Map<String, Integer> retryAfterTick = new HashMap<>();

    private static final int FAILED_SEARCH_BACKOFF_TICKS = 100;

    private record PendingAnnounce(String playerName, String modeName, String bracket, boolean join) {
    }

    private long lastBroadcastAt;
    private PendingAnnounce pending;
    private BukkitTask flushTask;

    public QueueManager(RTPQueuePlugin plugin, GearScanner scanner, LocationFinder finder) {
        this.plugin = plugin;
        this.scanner = scanner;
        this.finder = finder;
    }

    public Result toggle(Player player, QueueMode mode) {
        UUID id = player.getUniqueId();
        if (processing.contains(id)) {
            return Result.BUSY;
        }
        String current = queuedIn.get(id);
        if (mode.id().equals(current)) {
            remove(id);
            return Result.LEFT;
        }
        Result result = current == null ? Result.JOINED : Result.SWITCHED;
        if (current != null) {
            remove(id);
        }
        queues.computeIfAbsent(mode.id(), k -> new ArrayDeque<>()).addLast(id);
        queuedIn.put(id, mode.id());
        profile(player, true);
        return result;
    }

    public String leave(UUID id) {
        String mode = queuedIn.get(id);
        if (mode != null) {
            remove(id);
        }
        return mode;
    }

    public void onQuit(UUID id) {
        remove(id);
        profiles.remove(id);
    }

    public void clearAll() {
        queues.clear();
        queuedIn.clear();
    }

    public void onReload(Settings settings) {
        Set<String> valid = new HashSet<>();
        for (QueueMode m : settings.modes()) {
            valid.add(m.id());
        }
        for (Iterator<Map.Entry<String, ArrayDeque<UUID>>> it = queues.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ArrayDeque<UUID>> entry = it.next();
            if (valid.contains(entry.getKey())) {
                continue;
            }
            for (UUID id : entry.getValue()) {
                queuedIn.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    plugin.core().messages().send(p, "queue-mode-removed");
                }
            }
            it.remove();
        }
        profiles.clear();
    }

    public void shutdown() {
        queues.clear();
        queuedIn.clear();
        processing.clear();
        profiles.clear();
        retryAfterTick.clear();
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        pending = null;
    }

    public Optional<String> queuedMode(UUID id) {
        return Optional.ofNullable(queuedIn.get(id));
    }

    public boolean isProcessing(UUID id) {
        return processing.contains(id);
    }

    public void sendStatusActionBars() {
        if (queuedIn.isEmpty()) {
            return;
        }
        Settings s = plugin.settings();
        for (Map.Entry<UUID, String> e : queuedIn.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                continue;
            }
            String modeName = s.mode(e.getValue()).map(QueueMode::name).orElse(e.getValue());
            plugin.core().messages().send(p, "queue-status", "%mode%", modeName);
        }
    }

    public List<UUID> queued(String modeId) {
        ArrayDeque<UUID> q = queues.get(modeId);
        return q == null ? List.of() : List.copyOf(q);
    }

    public int waitingInBracket(String modeId, Player viewer) {
        ArrayDeque<UUID> q = queues.get(modeId);
        if (q == null || q.isEmpty()) {
            return 0;
        }
        String key = effectiveKey(viewer, false);
        int count = 0;
        for (UUID id : q) {
            if (id.equals(viewer.getUniqueId())) {
                continue;
            }
            Player other = Bukkit.getPlayer(id);
            if (other != null && effectiveKey(other, false).equals(key)) {
                count++;
            }
        }
        return count;
    }

    public GearProfile profile(Player player, boolean fresh) {
        Settings s = plugin.settings();
        int now = Bukkit.getCurrentTick();
        CachedProfile cached = profiles.get(player.getUniqueId());
        if (!fresh && cached != null && now - cached.tick() <= s.gearCacheTicks()) {
            return cached.profile();
        }
        GearProfile profile = scanner.scan(player, s.gear(), Bukkit.getWorld(s.rtp().world()));
        profiles.put(player.getUniqueId(), new CachedProfile(profile, now));
        return profile;
    }

    private String effectiveKey(Player p, boolean fresh) {
        return profile(p, fresh).bracketKey();
    }

    public void announce(Player player, String modeName, boolean join) {
        Settings s = plugin.settings();
        if (!s.broadcastEnabled()) {
            return;
        }
        String playerName = player.getName();
        String bracket = profile(player, true).bracketName(s.gear());
        long interval = s.broadcastMinIntervalMillis();
        long now = System.currentTimeMillis();
        if (now - lastBroadcastAt >= interval) {
            broadcastNow(playerName, modeName, bracket, join);
            lastBroadcastAt = now;
            return;
        }
        pending = new PendingAnnounce(playerName, modeName, bracket, join);
        if (flushTask == null) {
            long delayTicks = Math.max(1L, ((lastBroadcastAt + interval) - now) / 50L);
            flushTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::flush, delayTicks);
        }
    }

    private void flush() {
        flushTask = null;
        PendingAnnounce p = pending;
        pending = null;
        if (p == null) {
            return;
        }
        broadcastNow(p.playerName(), p.modeName(), p.bracket(), p.join());
        lastBroadcastAt = System.currentTimeMillis();
    }

    private void broadcastNow(String playerName, String modeName, String bracket, boolean join) {
        String key = join ? "queue-alert-join" : "queue-alert-leave";
        PlayerToggles toggles = plugin.core().playerToggles();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID id = viewer.getUniqueId();
            if (!toggles.isOn(id, PlayerToggles.Toggle.QUEUE_ALERTS)) {
                continue;
            }
            if (plugin.core().combatTag().isTagged(viewer)) {
                continue;
            }
            if (queuedIn.containsKey(id) || processing.contains(id)) {
                continue;
            }
            plugin.core().messages().send(viewer, key,
                    "%player%", playerName, "%mode%", modeName, "%bracket%", bracket);
            if (join) {
                plugin.core().sounds().play(viewer, "queue-alert");
            }
        }
    }

    public String modeBlockReason(Player p, QueueMode mode) {
        if (mode.noEnderChest() && profile(p, true).enderChest()) {
            return "blocked-ender-chest";
        }
        if (mode.noElytra() && isWearingElytra(p)) {
            return "blocked-elytra";
        }
        return null;
    }

    private static boolean isWearingElytra(Player p) {
        org.bukkit.inventory.ItemStack chest = p.getInventory().getChestplate();
        return chest != null && chest.getType() == org.bukkit.Material.ELYTRA;
    }

    public void matchmake() {
        Settings s = plugin.settings();
        int now = Bukkit.getCurrentTick();
        for (QueueMode mode : s.modes()) {
            ArrayDeque<UUID> q = queues.get(mode.id());
            if (q == null || q.size() < mode.players()) {
                continue;
            }
            Integer retryAt = retryAfterTick.get(mode.id());
            if (retryAt != null && now < retryAt) {
                continue;
            }
            Map<String, List<Player>> brackets = new LinkedHashMap<>();
            for (Iterator<UUID> it = q.iterator(); it.hasNext(); ) {
                UUID id = it.next();
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) {
                    it.remove();
                    queuedIn.remove(id);
                    continue;
                }
                if (!s.worldAllowed(p.getWorld())) {
                    it.remove();
                    queuedIn.remove(id);
                    plugin.core().messages().send(p, "queue-removed-world");
                    continue;
                }
                if (plugin.core().combatTag().isTagged(p)) {
                    continue;
                }
                brackets.computeIfAbsent(effectiveKey(p, false), k -> new ArrayList<>()).add(p);
            }
            for (List<Player> members : brackets.values()) {
                for (int i = 0; i + mode.players() <= members.size(); i += mode.players()) {
                    tryStart(mode, new ArrayList<>(members.subList(i, i + mode.players())), s);
                }
            }
        }
    }

    private void tryStart(QueueMode mode, List<Player> group, Settings s) {
        String key = null;
        for (Player p : group) {
            if (modeBlockReason(p, mode) != null) {
                return;
            }
            String k = effectiveKey(p, true);
            if (key == null) {
                key = k;
            } else if (!key.equals(k)) {
                return;
            }
        }
        ArrayDeque<UUID> q = queues.get(mode.id());
        List<UUID> ids = new ArrayList<>(group.size());
        for (Player p : group) {
            UUID id = p.getUniqueId();
            if (q != null) {
                q.remove(id);
            }
            queuedIn.remove(id);
            processing.add(id);
            ids.add(id);
        }
        String bracketName = profile(group.get(0), false).bracketName(s.gear());
        new Match(mode, ids, key, bracketName, s).start();
    }

    private void release(UUID id) {
        processing.remove(id);
    }

    private void remove(UUID id) {
        String mode = queuedIn.remove(id);
        if (mode != null) {
            ArrayDeque<UUID> q = queues.get(mode);
            if (q != null) {
                q.remove(id);
            }
        }
    }

    private void requeueFront(QueueMode mode, List<Player> players) {
        if (plugin.settings().mode(mode.id()).isEmpty()) {
            for (Player p : players) {
                plugin.core().messages().send(p, "queue-mode-removed");
            }
            return;
        }
        ArrayDeque<UUID> q = queues.computeIfAbsent(mode.id(), k -> new ArrayDeque<>());
        for (int i = players.size() - 1; i >= 0; i--) {
            UUID id = players.get(i).getUniqueId();
            if (queuedIn.containsKey(id)) {
                continue;
            }
            q.addFirst(id);
            queuedIn.put(id, mode.id());
        }
    }

    private final class Match {
        private final QueueMode mode;
        private final List<UUID> ids;
        private final String key;
        private final String bracketName;
        private final Settings s;

        Match(QueueMode mode, List<UUID> ids, String key, String bracketName, Settings s) {
            this.mode = mode;
            this.ids = ids;
            this.key = key;
            this.bracketName = bracketName;
            this.s = s;
        }

        void start() {
            for (Player p : online()) {
                plugin.core().messages().send(p, "match-found", "%mode%", mode.name(), "%bracket%", bracketName);
                plugin.core().sounds().play(p, "match-found");
            }
            World world = Bukkit.getWorld(s.rtp().world());
            if (world == null) {
                plugin.getLogger().warning("rtp.world '" + s.rtp().world() + "' is not loaded. The match was cancelled.");
                cancel("match-failed");
                return;
            }
            finder.findRandom(world, s.rtp())
                    .whenComplete((anchor, error) -> plugin.sync(() -> onAnchor(anchor, error)));
        }

        private void onAnchor(Location anchor, Throwable error) {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Spot search failed", error);
            }
            if (anchor == null) {
                retryAfterTick.put(mode.id(), Bukkit.getCurrentTick() + FAILED_SEARCH_BACKOFF_TICKS);
                cancel("match-failed");
                return;
            }
            List<Player> online = online();
            if (online.size() < ids.size()) {
                cancel("match-cancelled");
                return;
            }
            List<CompletableFuture<Location>> spots = new ArrayList<>();
            spots.add(CompletableFuture.completedFuture(anchor));
            for (int i = 1; i < online.size(); i++) {
                spots.add(finder.findNear(anchor, s.rtp()));
            }
            CompletableFuture.allOf(spots.toArray(new CompletableFuture[0]))
                    .whenComplete((ignored, err) -> plugin.sync(() -> onSpots(anchor, spots)));
        }

        private void onSpots(Location anchor, List<CompletableFuture<Location>> spots) {
            List<Player> online = online();
            if (online.size() < ids.size()) {
                cancel("match-cancelled");
                return;
            }
            List<Player> changed = new ArrayList<>();
            Map<UUID, GearProfile> finals = new HashMap<>();
            for (Player p : online) {
                finals.put(p.getUniqueId(), profile(p, true));
                if (!effectiveKey(p, true).equals(key) || modeBlockReason(p, mode) != null
                        || plugin.core().combatTag().isTagged(p)) {
                    changed.add(p);
                }
            }
            if (!changed.isEmpty()) {
                for (Player p : changed) {
                    release(p.getUniqueId());
                    if (plugin.core().combatTag().isTagged(p)) {
                        long seconds = (plugin.core().combatTag().remainingMillis(p) + 999L) / 1000L;
                        plugin.core().messages().send(p, "combat-tagged", "%seconds%", String.valueOf(seconds));
                    } else {
                        plugin.core().messages().send(p, "queue-gear-changed");
                    }
                    plugin.core().sounds().play(p, "error");
                }
                List<Player> rest = new ArrayList<>(online);
                rest.removeAll(changed);
                requeueFront(mode, rest);
                for (Player p : rest) {
                    release(p.getUniqueId());
                    plugin.core().messages().send(p, "match-cancelled");
                    plugin.core().sounds().play(p, "error");
                }
                plugin.scheduleMatchmake();
                return;
            }
            for (int i = 0; i < online.size(); i++) {
                Player p = online.get(i);
                Location dest = anchor;
                if (i < spots.size()) {
                    CompletableFuture<Location> f = spots.get(i);
                    if (!f.isCompletedExceptionally()) {
                        Location found = f.getNow(null);
                        if (found != null) {
                            dest = found;
                        }
                    }
                }
                teleport(p, dest, regearWarning(p, online, finals));
            }
        }

        private List<String> regearWarning(Player viewer, List<Player> group, Map<UUID, GearProfile> finals) {
            if (!s.gear().warnEnderChest()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (Player other : group) {
                if (other.equals(viewer)) {
                    continue;
                }
                GearProfile gp = finals.get(other.getUniqueId());
                if (gp != null && gp.enderChest()) {
                    names.add(other.getName());
                }
            }
            return names;
        }

        private void teleport(Player p, Location dest, List<String> regearNames) {
            UUID id = p.getUniqueId();
            try {
                p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.COMMAND)
                        .whenComplete((ok, error) -> {
                            boolean scheduled = plugin.sync(() -> {
                                try {
                                    Player now = Bukkit.getPlayer(id);
                                    if (now == null) {
                                        return;
                                    }
                                    if (error != null || !Boolean.TRUE.equals(ok)) {
                                        plugin.core().messages().send(now, "teleport-blocked");
                                        plugin.core().sounds().play(now, "error");
                                        return;
                                    }
                                    landed(now, regearNames);
                                } finally {
                                    release(id);
                                }
                            });
                            if (!scheduled) {
                                release(id);
                            }
                        });
            } catch (Throwable t) {
                release(id);
                plugin.getLogger().log(Level.WARNING, "Teleport failed for " + p.getName(), t);
            }
        }

        private void landed(Player p, List<String> regearNames) {
            if (s.glowSeconds() > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, s.glowSeconds() * 20, 0, false, false, true));
            }
            plugin.core().combatTag().tag(p, "rtp-match");
            plugin.core().messages().send(p, "match-landed", "%mode%", mode.name(), "%bracket%", bracketName);
            plugin.core().sounds().play(p, "match-landed");
            if (!regearNames.isEmpty()) {
                plugin.core().messages().send(p, "ender-chest-warning", "%players%", String.join(", ", regearNames));
                plugin.core().sounds().play(p, "warning");
            }
            Location l = p.getLocation();
            plugin.runCommands(s.onMatchCommands(),
                    "%player%", p.getName(),
                    "%mode%", mode.id(),
                    "%bracket%", bracketName,
                    "%world%", l.getWorld() == null ? "" : l.getWorld().getName(),
                    "%x%", String.valueOf(l.getBlockX()),
                    "%y%", String.valueOf(l.getBlockY()),
                    "%z%", String.valueOf(l.getBlockZ()));
        }

        private void cancel(String messageKey) {
            List<Player> online = online();
            requeueFront(mode, online);
            for (UUID id : ids) {
                release(id);
            }
            for (Player p : online) {
                plugin.core().messages().send(p, messageKey);
                plugin.core().sounds().play(p, "error");
            }
        }

        private List<Player> online() {
            List<Player> list = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    list.add(p);
                }
            }
            return list;
        }
    }
}
