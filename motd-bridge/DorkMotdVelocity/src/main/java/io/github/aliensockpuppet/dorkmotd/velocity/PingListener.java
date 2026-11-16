package io.github.aliensockpuppet.dorkmotd.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;

public final class PingListener {
    private final MotdManager motd;

    public PingListener(MotdManager motd) {
        this.motd = motd;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPing(ProxyPingEvent event) {
        Component current = motd.current();
        if (current == null) {
            return;
        }
        ServerPing updated = event.getPing().asBuilder()
                .description(current)
                .build();
        event.setPing(updated);
    }
}
