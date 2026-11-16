package io.github.aliensockpuppet.dorkcore.service;

import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.Sounds;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

public final class SoundsImpl implements Sounds {
    private volatile FileConfiguration yaml;
    private volatile PlayerToggles toggles;

    public void load(FileConfiguration yaml) {
        this.yaml = yaml;
    }

    public void setToggles(PlayerToggles toggles) {
        this.toggles = toggles;
    }

    @Override
    public void play(Player player, String key) {
        FileConfiguration y = this.yaml;
        if (player == null || y == null) {
            return;
        }
        PlayerToggles t = this.toggles;
        if (t != null && !t.isOn(player.getUniqueId(), PlayerToggles.Toggle.MESSAGE_STYLE)) {
            return;
        }
        String base = "sounds." + key + ".";
        String name = y.getString(base + "sound");
        if (name == null || name.isBlank()) {
            return;
        }
        Sound sound = resolve(name);
        if (sound == null) {
            return;
        }
        float volume = (float) y.getDouble(base + "volume", 1.0D);
        float pitch = (float) y.getDouble(base + "pitch", 1.0D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static Sound resolve(String name) {
        NamespacedKey nk = NamespacedKey.fromString(name.toLowerCase());
        if (nk == null) {
            return null;
        }
        return Registry.SOUNDS.get(nk);
    }
}
