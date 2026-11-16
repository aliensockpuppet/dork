package io.github.aliensockpuppet.dorkcore.api;

import java.util.UUID;

public interface PlayerToggles {
    enum Toggle {
        CHAT(true),

        TPA_REQUESTS(true),

        TPA_AUTO(false),

        JOIN_LEAVE_MESSAGES(true),

        DEATH_MESSAGES(true),

        PHANTOM_SPAWNS(true),

        RECEIVE_PMS(true),

        QUEUE_ALERTS(false),

        MESSAGE_STYLE(true);

        private final boolean defaultOn;

        Toggle(boolean defaultOn) {
            this.defaultOn = defaultOn;
        }

        public boolean defaultOn() {
            return defaultOn;
        }
    }

    boolean isOn(UUID player, Toggle toggle);

    void set(UUID player, Toggle toggle, boolean on);

    boolean flip(UUID player, Toggle toggle);
}
