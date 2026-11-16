package io.github.aliensockpuppet.dorkcore.service;

import io.github.aliensockpuppet.dorkcore.api.AuthGate;
import io.github.aliensockpuppet.dorkcore.api.Colors;
import io.github.aliensockpuppet.dorkcore.api.CombatTag;
import io.github.aliensockpuppet.dorkcore.api.Cooldowns;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.dorkcore.api.Homes;
import io.github.aliensockpuppet.dorkcore.api.Messages;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;
import io.github.aliensockpuppet.dorkcore.api.Sounds;

public final class DorkCoreApiImpl implements DorkCoreApi {
    private final Messages messages;
    private final Sounds sounds;
    private final Colors colors;
    private final Cooldowns cooldowns;
    private final CombatTag combatTag;
    private final AuthGate authGate;
    private final PlayerToggles playerToggles;
    private final Homes homes;

    public DorkCoreApiImpl(Messages messages, Sounds sounds, Colors colors,
                           Cooldowns cooldowns, CombatTag combatTag, AuthGate authGate,
                           PlayerToggles playerToggles, Homes homes) {
        this.messages = messages;
        this.sounds = sounds;
        this.colors = colors;
        this.cooldowns = cooldowns;
        this.combatTag = combatTag;
        this.authGate = authGate;
        this.playerToggles = playerToggles;
        this.homes = homes;
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public Messages messages() {
        return messages;
    }

    @Override
    public Sounds sounds() {
        return sounds;
    }

    @Override
    public Colors colors() {
        return colors;
    }

    @Override
    public Cooldowns cooldowns() {
        return cooldowns;
    }

    @Override
    public CombatTag combatTag() {
        return combatTag;
    }

    @Override
    public AuthGate authGate() {
        return authGate;
    }

    @Override
    public PlayerToggles playerToggles() {
        return playerToggles;
    }

    @Override
    public Homes homes() {
        return homes;
    }
}
