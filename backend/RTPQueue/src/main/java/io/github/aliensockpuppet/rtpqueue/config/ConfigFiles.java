package io.github.aliensockpuppet.rtpqueue.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigFiles {
    private ConfigFiles() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name, int version) {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create " + dir.getPath());
        }
        File file = new File(dir, name);
        if (file.exists()) {
            int found = YamlConfiguration.loadConfiguration(file).getInt("config-version", 0);
            if (found != version) {
                String stem = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
                File backup = new File(dir, stem + "-v" + found + "-backup-" + System.currentTimeMillis() + ".yml");
                if (file.renameTo(backup)) {
                    plugin.getLogger().warning(name + " is from another version (found " + found + ", need " + version
                            + "). Moved it to " + backup.getName() + " and wrote a fresh " + name + ".");
                } else {
                    plugin.getLogger().warning("Could not back up the old " + name + ". Using it as-is.");
                }
            }
        }
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read the default " + name + ": " + e.getMessage());
        }
        return yaml;
    }
}
