package io.github.aliensockpuppet.dorkmenu.menu;

import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.github.aliensockpuppet.dorkmenu.DorkMenuPlugin;

public final class MenuClickListener implements Listener {
    private static final String NAMESPACE = "dorkmenu";

    private final DorkMenuPlugin plugin;
    private final MenuService menu;

    public MenuClickListener(DorkMenuPlugin plugin, MenuService menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (id == null || !NAMESPACE.equals(id.namespace())) {
            return;
        }
        if (!(event.getCommonConnection() instanceof io.papermc.paper.connection.PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        if (player == null) {
            return;
        }
        String action = id.value();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p != null && p.isOnline()) {
                menu.dispatch(p, action);
            }
        });
    }
}
