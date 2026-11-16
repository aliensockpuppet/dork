package io.github.aliensockpuppet.dorkcore.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.Messages;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

import java.util.List;

public final class MessagesImpl implements Messages {
    private final MiniMessage mini;
    private volatile FileConfiguration yaml;
    private volatile PlayerToggles toggles;

    public MessagesImpl(MiniMessage mini) {
        this.mini = mini;
    }

    public void load(FileConfiguration yaml) {
        this.yaml = yaml;
    }

    public void setToggles(PlayerToggles toggles) {
        this.toggles = toggles;
    }

    private boolean isLegacy(CommandSender to) {
        PlayerToggles t = this.toggles;
        if (t == null || !(to instanceof Player player)) {
            return false;
        }
        return !t.isOn(player.getUniqueId(), PlayerToggles.Toggle.MESSAGE_STYLE);
    }

    @Override
    public boolean has(String key) {
        FileConfiguration y = this.yaml;
        return y != null && y.contains("messages." + key);
    }

    @Override
    public String raw(String key, String fallback) {
        FileConfiguration y = this.yaml;
        if (y == null) {
            return fallback;
        }
        String line = first(y, "messages." + key + ".chat.message");
        return line == null ? fallback : line;
    }

    @Override
    public void send(CommandSender to, String key, String... pairs) {
        FileConfiguration y = this.yaml;
        if (to == null || y == null) {
            return;
        }
        String base = "messages." + key + ".";
        if (!y.contains("messages." + key)) {
            return;
        }

        if (isLegacy(to)) {
            sendLegacy(to, y, base, pairs);
            return;
        }

        sendChat(to, y, base, pairs);
        if (to instanceof Player player) {
            sendActionBar(player, y, base, pairs);
            sendTitle(player, y, base, pairs);
        }
    }

    private void sendLegacy(CommandSender to, FileConfiguration y, String base, String[] pairs) {
        if (y.getBoolean(base + "legacy.suppress", false)) {
            return;
        }
        List<String> legacyLines = y.getStringList(base + "legacy.message");
        if (!legacyLines.isEmpty()) {
            for (String line : legacyLines) {
                to.sendMessage(parse(line, pairs));
            }
            return;
        }
        if (y.getBoolean(base + "chat.enabled")) {
            sendChat(to, y, base, pairs);
            return;
        }
        String ab = first(y, base + "actionbar.message");
        if (ab != null) {
            to.sendMessage(parse(ab, pairs));
        }
    }

    private void sendChat(CommandSender to, FileConfiguration y, String base, String[] pairs) {
        if (!y.getBoolean(base + "chat.enabled")) {
            return;
        }
        for (String line : y.getStringList(base + "chat.message")) {
            to.sendMessage(parse(line, pairs));
        }
    }

    private void sendActionBar(Player player, FileConfiguration y, String base, String[] pairs) {
        if (!y.getBoolean(base + "actionbar.enabled")) {
            return;
        }
        String line = first(y, base + "actionbar.message");
        if (line != null) {
            player.sendActionBar(parse(line, pairs));
        }
    }

    private void sendTitle(Player player, FileConfiguration y, String base, String[] pairs) {
        boolean title = y.getBoolean(base + "title.enabled");
        boolean subtitle = y.getBoolean(base + "subtitle.enabled");
        if (!title && !subtitle) {
            return;
        }
        Component main = title ? parse(orEmpty(first(y, base + "title.message")), pairs) : Component.empty();
        Component sub = subtitle ? parse(orEmpty(first(y, base + "subtitle.message")), pairs) : Component.empty();
        player.showTitle(Title.title(main, sub));
    }

    private Component parse(String line, String[] pairs) {
        return mini.deserialize(apply(line, pairs));
    }

    private static String apply(String line, String[] pairs) {
        if (line == null) {
            return "";
        }
        String out = line;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String token = pairs[i];
            if (token == null || token.isEmpty()) {
                continue;
            }
            String value = safeValue(pairs[i + 1]);
            out = out.replace(token, value);
            String alt = altToken(token);
            if (alt != null) {
                out = out.replace(alt, value);
            }
        }
        return out;
    }

    private static String altToken(String token) {
        int len = token.length();
        if (len >= 2 && token.charAt(0) == '%' && token.charAt(len - 1) == '%') {
            return "<" + token.substring(1, len - 1) + ">";
        }
        if (len >= 2 && token.charAt(0) == '<' && token.charAt(len - 1) == '>') {
            return "%" + token.substring(1, len - 1) + "%";
        }
        return null;
    }

    private static String safeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('<', ' ').replace('>', ' ').replace('§', ' ');
    }

    private static String first(ConfigurationSection section, String path) {
        List<String> lines = section.getStringList(path);
        return lines.isEmpty() ? null : lines.get(0);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
