package io.github.aliensockpuppet.dorkcore.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IconText {
    private static final Pattern ICON_TAG = Pattern.compile(
            "<(item|head):([a-zA-Z0-9_:/.-]+)>", Pattern.CASE_INSENSITIVE);
    private static final Key ITEMS_ATLAS = Key.key("minecraft", "items");
    private static final Key BLOCKS_ATLAS = Key.key("minecraft", "blocks");
    private static final Key GUI_ATLAS = Key.key("minecraft", "gui");

    private IconText() {
    }

    public static boolean hasIconTag(String text) {
        if (text == null || text.indexOf('<') < 0) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("<item:") || lower.contains("<head:");
    }

    public static String stripIconTags(String raw) {
        if (raw == null) {
            return "";
        }
        if (!hasIconTag(raw)) {
            return raw;
        }
        return ICON_TAG.matcher(raw).replaceAll("").replaceAll("  +", " ").strip();
    }

    public static Component render(MiniMessage mini, String raw) {
        String text = raw == null ? "" : raw;
        if (!hasIconTag(text)) {
            return mini.deserialize(text);
        }
        Matcher m = ICON_TAG.matcher(text);
        Component out = Component.empty();
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out = out.append(mini.deserialize(text.substring(last, m.start())));
            }
            Component icon = "head".equalsIgnoreCase(m.group(1))
                    ? playerHead(m.group(2))
                    : itemSprite(m.group(2));
            if (icon != null) {
                out = out.append(icon.decoration(TextDecoration.ITALIC, false));
            }
            last = m.end();
        }
        if (last < text.length()) {
            out = out.append(mini.deserialize(text.substring(last)));
        }
        return out;
    }

    private static Component itemSprite(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String trimmed = raw.trim().toLowerCase(Locale.ROOT);
            String namespace = "minecraft";
            String path = trimmed;
            int colon = trimmed.indexOf(':');
            if (colon >= 0) {
                namespace = trimmed.substring(0, colon);
                path = trimmed.substring(colon + 1);
            }
            if (!path.startsWith("item/") && !path.startsWith("block/") && !path.startsWith("gui/")) {
                path = "item/" + path;
            }
            Key atlas = path.startsWith("block/") ? BLOCKS_ATLAS
                    : path.startsWith("gui/") ? GUI_ATLAS : ITEMS_ATLAS;
            return Component.object(ObjectContents.sprite(atlas, Key.key(namespace, path)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Component playerHead(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String arg = raw.trim();
            boolean looksLikeName = arg.indexOf('/') < 0 && arg.indexOf(':') < 0
                    && arg.matches("[A-Za-z0-9_]{1,16}");
            if (looksLikeName) {
                return Component.object(ObjectContents.playerHead().name(arg).hat(true).build());
            }
            String lower = arg.toLowerCase(Locale.ROOT);
            int colon = lower.indexOf(':');
            Key texture = colon >= 0
                    ? Key.key(lower.substring(0, colon), lower.substring(colon + 1))
                    : Key.key("minecraft", lower);
            return Component.object(ObjectContents.playerHead().texture(texture).hat(false).build());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
