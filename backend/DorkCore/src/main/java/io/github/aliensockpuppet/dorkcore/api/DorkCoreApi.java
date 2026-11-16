package io.github.aliensockpuppet.dorkcore.api;

public interface DorkCoreApi {
    int API_VERSION = 11;

    int apiVersion();

    Messages messages();

    Sounds sounds();

    Colors colors();

    Cooldowns cooldowns();

    CombatTag combatTag();

    AuthGate authGate();

    PlayerToggles playerToggles();

    Homes homes();
}
