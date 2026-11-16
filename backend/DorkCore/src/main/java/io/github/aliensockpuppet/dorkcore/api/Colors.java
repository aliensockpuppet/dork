package io.github.aliensockpuppet.dorkcore.api;

import net.kyori.adventure.text.Component;

public interface Colors {
    Component action(String text);

    Component destructive(String text);

    Component neutral(String text);

    Component render(String miniMessage);
}
