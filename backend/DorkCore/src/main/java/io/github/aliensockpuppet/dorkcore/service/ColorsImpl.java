package io.github.aliensockpuppet.dorkcore.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.github.aliensockpuppet.dorkcore.api.Colors;
import io.github.aliensockpuppet.dorkcore.api.IconText;

public final class ColorsImpl implements Colors {
    private static final String GREEN = "#55ff55";
    private static final String RED = "#ff5555";
    private static final String GRAY = "#aaaaaa";

    private final MiniMessage mini;

    public ColorsImpl(MiniMessage mini) {
        this.mini = mini;
    }

    @Override
    public Component action(String text) {
        return role(GREEN, text);
    }

    @Override
    public Component destructive(String text) {
        return role(RED, text);
    }

    @Override
    public Component neutral(String text) {
        return role(GRAY, text);
    }

    @Override
    public Component render(String miniMessage) {
        return IconText.render(mini, miniMessage);
    }

    private Component role(String hex, String text) {
        String safe = text == null ? "" : text;
        return mini.deserialize("<i:false><color:" + hex + ">" + escape(safe) + "</color>");
    }

    private static String escape(String raw) {
        return raw.replace("<", "\\<").replace("\u00A7", "");
    }
}
