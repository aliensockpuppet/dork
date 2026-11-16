package io.github.aliensockpuppet.dorksurvey;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class SurveyStore {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration data;

    public SurveyStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            this.data = new YamlConfiguration();
            return;
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public Set<String> answeredIds(UUID player) {
        ConfigurationSection answers = data.getConfigurationSection("players." + player + ".answers");
        return answers == null ? new HashSet<>() : new HashSet<>(answers.getKeys(false));
    }

    public void putAnswer(UUID player, String questionId, String optionId) {
        data.set("players." + player + ".answers." + questionId, optionId);
        save();
    }

    public boolean rewarded(UUID player) {
        return data.getBoolean("players." + player + ".rewarded", false);
    }

    public void setRewarded(UUID player) {
        data.set("players." + player + ".rewarded", true);
        save();
    }

    private String ipSalt() {
        String salt = data.getString("ip-salt");
        if (salt == null || salt.isBlank()) {
            byte[] bytes = new byte[16];
            new java.security.SecureRandom().nextBytes(bytes);
            salt = java.util.HexFormat.of().formatHex(bytes);
            data.set("ip-salt", salt);
            save();
        }
        return salt;
    }

    public String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((ipSalt() + "|" + ip).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(out);
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    public boolean ipRewarded(String ipHash) {
        return ipHash != null && data.getStringList("rewarded-ips").contains(ipHash);
    }

    public void addRewardedIp(String ipHash) {
        if (ipHash == null) {
            return;
        }
        java.util.List<String> list = new java.util.ArrayList<>(data.getStringList("rewarded-ips"));
        if (!list.contains(ipHash)) {
            list.add(ipHash);
            data.set("rewarded-ips", list);
            save();
        }
    }

    public void reset(UUID player) {
        data.set("players." + player, null);
        save();
    }

    private void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            File tmp = new File(dir, file.getName() + ".tmp");
            data.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save DorkSurvey data.yml", e);
        }
    }
}
