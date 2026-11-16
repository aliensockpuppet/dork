package io.github.aliensockpuppet.dorkcore.command;

import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class TpaDialogListener implements Listener {
    private final JavaPlugin plugin;
    private final TpaCommand tpa;

    public TpaDialogListener(JavaPlugin plugin, TpaCommand tpa) {
        this.plugin = plugin;
        this.tpa = tpa;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (id == null || !"dorkcore".equals(id.namespace())) {
            return;
        }
        if (!TpaCommand.CONFIRM.equals(id)) {
            return;
        }
        if (!(event.getCommonConnection() instanceof io.papermc.paper.connection.PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p == null || !p.isOnline()) {
                return;
            }
            p.closeDialog();
            tpa.confirmFor(p);
        });
    }
}
