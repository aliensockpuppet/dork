package io.github.aliensockpuppet.dorkcombat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public final class CombatListener implements Listener {
    private static final long CRYSTAL_HIT_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long EXPLOSION_TAG_MILLIS = 3500L;
    private static final double EXPLOSION_TAG_RADIUS = 20.0;

    private enum ExplosionKind { END_CRYSTAL, RESPAWN_ANCHOR, TNT_MINECART }

    private record ExplosionTag(Location center, long timeMs, ExplosionKind kind) {}

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final CombatManager combat;
    private final DorkCoreApi core;

    private final Map<UUID, long[]> crystalHitTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> crystalHitter = new ConcurrentHashMap<>();
    private final List<ExplosionTag> recentExplosions = new CopyOnWriteArrayList<>();

    private final Set<UUID> exemptKick = ConcurrentHashMap.newKeySet();

    public CombatListener(org.bukkit.plugin.java.JavaPlugin plugin, CombatManager combat, DorkCoreApi core) {
        this.plugin = plugin;
        this.combat = combat;
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) {
            Player hitter = resolvePlayer(event.getDamager());
            if (hitter != null) {
                crystalHitter.put(crystal.getUniqueId(), hitter.getUniqueId());
                crystalHitTime.put(crystal.getUniqueId(), new long[]{System.currentTimeMillis()});
                cleanupCrystals();
            }
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        combat.tagPair(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!combat.tagExplosions()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        ExplosionKind kind = classifyExplosion(event, victim);
        if (kind == null) {
            return;
        }
        Player attacker = explosionAttacker(event, kind);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        combat.tagPair(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!combat.tagExplosions()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof EnderCrystal) {
            registerExplosion(entity.getLocation(), ExplosionKind.END_CRYSTAL);
        } else if (entity.getType() == EntityType.TNT_MINECART) {
            registerExplosion(entity.getLocation(), ExplosionKind.TNT_MINECART);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!combat.tagExplosions()) {
            return;
        }
        if (event.getExplodedBlockState().getType() == Material.RESPAWN_ANCHOR) {
            registerExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), ExplosionKind.RESPAWN_ANCHOR);
        }
    }

    private void registerExplosion(Location center, ExplosionKind kind) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        recentExplosions.removeIf(tag -> now - tag.timeMs() > EXPLOSION_TAG_MILLIS);
        recentExplosions.add(new ExplosionTag(center.clone(), now, kind));
    }

    private ExplosionKind classifyExplosion(EntityDamageEvent event, Player victim) {
        var source = event.getDamageSource();
        Entity direct = source != null ? source.getDirectEntity() : null;
        if (direct instanceof EnderCrystal) {
            return ExplosionKind.END_CRYSTAL;
        }
        if (direct != null && direct.getType() == EntityType.TNT_MINECART) {
            return ExplosionKind.TNT_MINECART;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && source != null) {
            Location loc = source.getDamageLocation();
            if (loc != null && loc.getWorld() != null
                    && loc.getBlock().getType() == Material.RESPAWN_ANCHOR) {
                return ExplosionKind.RESPAWN_ANCHOR;
            }
        }
        if (isNearRecentExplosion(victim, ExplosionKind.END_CRYSTAL)) {
            return ExplosionKind.END_CRYSTAL;
        }
        if (isNearRecentExplosion(victim, ExplosionKind.TNT_MINECART)) {
            return ExplosionKind.TNT_MINECART;
        }
        if (isNearRecentExplosion(victim, ExplosionKind.RESPAWN_ANCHOR)) {
            return ExplosionKind.RESPAWN_ANCHOR;
        }
        return null;
    }

    private Player explosionAttacker(EntityDamageEvent event, ExplosionKind kind) {
        var source = event.getDamageSource();
        if (source != null) {
            Player causing = asOnlinePlayer(source.getCausingEntity());
            if (causing != null) {
                return causing;
            }
            Entity direct = source.getDirectEntity();
            if (kind == ExplosionKind.END_CRYSTAL && direct instanceof EnderCrystal crystal) {
                Player hitter = rememberedHitter(crystal.getUniqueId());
                if (hitter != null) {
                    return hitter;
                }
            }
        }
        if (kind == ExplosionKind.END_CRYSTAL) {
            Player hitter = nearestRecentCrystalHitter(event.getEntity().getLocation());
            if (hitter != null) {
                return hitter;
            }
        }
        return null;
    }

    private boolean isNearRecentExplosion(Player victim, ExplosionKind kind) {
        if (victim == null || !victim.isOnline()) {
            return false;
        }
        long now = System.currentTimeMillis();
        World world = victim.getWorld();
        Location victimLoc = victim.getLocation();
        double radiusSquared = EXPLOSION_TAG_RADIUS * EXPLOSION_TAG_RADIUS;
        for (ExplosionTag tag : recentExplosions) {
            if (tag.kind() != kind || now - tag.timeMs() > EXPLOSION_TAG_MILLIS) {
                continue;
            }
            Location center = tag.center();
            if (center.getWorld() == null || !center.getWorld().equals(world)) {
                continue;
            }
            if (victimLoc.distanceSquared(center) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private Player nearestRecentCrystalHitter(Location where) {
        if (where == null || where.getWorld() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        double radiusSquared = EXPLOSION_TAG_RADIUS * EXPLOSION_TAG_RADIUS;
        Player best = null;
        long bestTime = Long.MIN_VALUE;
        for (Map.Entry<UUID, long[]> e : crystalHitTime.entrySet()) {
            long when = e.getValue()[0];
            if (now - when > CRYSTAL_HIT_TTL_MILLIS) {
                continue;
            }
            UUID hitterId = crystalHitter.get(e.getKey());
            Player hitter = hitterId == null ? null : asOnlinePlayer(org.bukkit.Bukkit.getPlayer(hitterId));
            if (hitter == null || !hitter.getWorld().equals(where.getWorld())) {
                continue;
            }
            if (hitter.getLocation().distanceSquared(where) <= radiusSquared && when > bestTime) {
                best = hitter;
                bestTime = when;
            }
        }
        return best;
    }

    private Player rememberedHitter(UUID crystalId) {
        long[] when = crystalHitTime.get(crystalId);
        if (when == null || System.currentTimeMillis() - when[0] > CRYSTAL_HIT_TTL_MILLIS) {
            crystalHitTime.remove(crystalId);
            crystalHitter.remove(crystalId);
            return null;
        }
        UUID hitter = crystalHitter.get(crystalId);
        return hitter == null ? null : asOnlinePlayer(org.bukkit.Bukkit.getPlayer(hitter));
    }

    private void cleanupCrystals() {
        long now = System.currentTimeMillis();
        crystalHitTime.entrySet().removeIf(e -> now - e.getValue()[0] > CRYSTAL_HIT_TTL_MILLIS);
        crystalHitter.keySet().removeIf(id -> !crystalHitTime.containsKey(id));
    }

    private static Player asOnlinePlayer(Entity entity) {
        return entity instanceof Player p && p.isOnline() ? p : null;
    }

    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCombatDeathCredit(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getKiller() != null) {
            return;
        }
        UUID opponentId = combat.opponentOf(victim.getUniqueId());
        if (opponentId == null || opponentId.equals(victim.getUniqueId())) {
            return;
        }
        Player opponent = org.bukkit.Bukkit.getPlayer(opponentId);
        if (opponent != null && opponent.isOnline()) {
            victim.setKiller(opponent);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        combat.clear(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLand(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (combat.isTagged(player)) {
            combat.tag(player, "pearl-land");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent event) {
        if (!combat.exemptKicks()) {
            return;
        }
        Player player = event.getPlayer();
        if (combat.isTagged(player)) {
            exemptKick.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combat.isTagged(player)) {
            exemptKick.remove(player.getUniqueId());
            return;
        }
        if (exemptKick.remove(player.getUniqueId())) {
            combat.clear(player.getUniqueId());
            return;
        }

        java.util.UUID opponentId = combat.opponentOf(player.getUniqueId());
        combat.clear(player.getUniqueId());

        dropAndClear(player);

        creditCombatLogKill(player, opponentId);
    }

    private void creditCombatLogKill(Player quitter, java.util.UUID opponentId) {
        if (opponentId == null) {
            return;
        }
        Player opponent = org.bukkit.Bukkit.getPlayer(opponentId);
        combat.clear(opponentId);

        if (opponent != null && opponent.isOnline()) {
            core.messages().send(opponent, "combat-log-kill", "%player%", quitter.getName());
            core.sounds().play(opponent, "success");
        }

        io.github.aliensockpuppet.dorkstats.api.DorkStatsApi stats = statsApi();
        if (stats == null) {
            return;
        }
        String opponentName = opponent != null ? opponent.getName()
                : org.bukkit.Bukkit.getOfflinePlayer(opponentId).getName();
        try {
            stats.recordCombatLogKill(opponentId, opponentName, quitter.getUniqueId(), quitter.getName());
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not record combat-log kill for " + quitter.getName(), e);
        }
    }

    private io.github.aliensockpuppet.dorkstats.api.DorkStatsApi statsApi() {
        try {
            org.bukkit.plugin.RegisteredServiceProvider<io.github.aliensockpuppet.dorkstats.api.DorkStatsApi> rsp =
                    org.bukkit.Bukkit.getServicesManager().getRegistration(io.github.aliensockpuppet.dorkstats.api.DorkStatsApi.class);
            return rsp == null ? null : rsp.getProvider();
        } catch (NoClassDefFoundError | RuntimeException e) {
            return null;
        }
    }

    private void dropAndClear(Player player) {
        if (player.getWorld() == null) {
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents().clone();
        ItemStack[] armor = player.getInventory().getArmorContents().clone();
        ItemStack offhand = player.getInventory().getItemInOffHand().clone();
        ItemStack cursor = player.getItemOnCursor().clone();
        int xp = totalExperience(player);
        player.getInventory().clear();
        player.setItemOnCursor(null);
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0f);

        var loc = player.getLocation();
        dropAll(player, loc, storage);
        dropAll(player, loc, armor);
        if (offhand.getType() != org.bukkit.Material.AIR) {
            player.getWorld().dropItemNaturally(loc, offhand);
        }
        if (cursor.getType() != org.bukkit.Material.AIR) {
            player.getWorld().dropItemNaturally(loc, cursor);
        }
        if (xp > 0) {
            org.bukkit.entity.ExperienceOrb orb = player.getWorld().spawn(loc, org.bukkit.entity.ExperienceOrb.class);
            orb.setExperience(Math.min(xp, 100));
        }

        try {
            player.saveData();
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not force-save after combat-log strip for " + player.getName(), e);
        }
    }

    private static int totalExperience(Player player) {
        int level = player.getLevel();
        int points = xpAtStartOfLevel(level);
        points += Math.round(player.getExp() * player.getExpToLevel());
        return Math.max(0, points);
    }

    private static int xpAtStartOfLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    private void dropAll(Player player, org.bukkit.Location loc, ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                player.getWorld().dropItemNaturally(loc, item);
            }
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
