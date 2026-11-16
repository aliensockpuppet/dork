package io.github.aliensockpuppet.dorkrestart;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.logging.Level;

public final class RestartScheduler {
    private final ZoneId zone;

    private final DorkRestartPlugin plugin;
    private final DorkCoreApi core;
    private final LocalTime restartTime;
    private final boolean clearCombatTags;
    private final String method;
    private final String command;
    private final NavigableSet<Integer> warnings;

    private BukkitTask ticker;
    private long targetEpochSeconds;
    private int lastWarningSent = Integer.MAX_VALUE;
    private boolean restarting;

    public RestartScheduler(DorkRestartPlugin plugin, DorkCoreApi core, LocalTime restartTime,
                            boolean clearCombatTags, String method, String command, List<Integer> warnings,
                            ZoneId zone) {
        this.plugin = plugin;
        this.zone = zone;
        this.core = core;
        this.restartTime = restartTime;
        this.clearCombatTags = clearCombatTags;
        this.method = method == null ? "shutdown" : method.toLowerCase(java.util.Locale.ROOT);
        this.command = command == null ? "" : command;
        this.warnings = new TreeSet<>();
        if (warnings != null) {
            for (Integer w : warnings) {
                if (w != null && w > 0) {
                    this.warnings.add(w);
                }
            }
        }
    }

    public void start() {
        this.targetEpochSeconds = nextRestartEpochSeconds();
        this.lastWarningSent = Integer.MAX_VALUE;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Next restart at " + restartTime + " " + zone.getId() + " ("
                + secondsUntil() + "s from now).");
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public long secondsUntil() {
        return targetEpochSeconds - (System.currentTimeMillis() / 1000L);
    }

    public void restartNow(int countdownSeconds) {
        if (restarting) {
            return;
        }
        this.targetEpochSeconds = (System.currentTimeMillis() / 1000L) + Math.max(1, countdownSeconds);
        this.lastWarningSent = Integer.MAX_VALUE;
    }

    private long nextRestartEpochSeconds() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.with(restartTime).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toEpochSecond();
    }

    private void tick() {
        if (restarting) {
            return;
        }
        long left = secondsUntil();
        if (left <= 0) {
            doRestart();
            return;
        }
        Integer due = left > Integer.MAX_VALUE ? null : warnings.ceiling((int) left);
        if (due != null && due < lastWarningSent) {
            lastWarningSent = due;
            broadcastWarning(due);
        }
    }

    private void broadcastWarning(int seconds) {
        String time = format(seconds);
        for (Player p : Bukkit.getOnlinePlayers()) {
            core.messages().send(p, "restart-warning", "%time%", time);
            core.sounds().play(p, "warning");
        }
    }

    private void doRestart() {
        restarting = true;
        stop();
        if (clearCombatTags) {
            try {
                core.combatTag().clearAll();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not clear combat tags before restart", e);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            core.messages().send(p, "restart-now");
        }
        plugin.getLogger().info("Scheduled restart: stopping the server now.");
        if (method.equals("command") && !command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            Bukkit.getScheduler().runTaskLater(plugin, this::recoverIfStillAlive, 200L);
        } else {
            Bukkit.shutdown();
        }
    }

    private void recoverIfStillAlive() {
        if (!restarting) {
            return;
        }
        plugin.getLogger().warning("Restart command did not stop the server. Check restart.command. "
                + "Rescheduling the next restart so it is not skipped.");
        restarting = false;
        if (ticker == null) {
            start();
        }
    }

    private static String format(int seconds) {
        if (seconds % 60 == 0 && seconds >= 60) {
            int minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    public boolean isRestarting() {
        return restarting;
    }
}
