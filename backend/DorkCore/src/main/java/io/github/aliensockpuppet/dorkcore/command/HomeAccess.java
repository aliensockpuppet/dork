package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;
import java.util.regex.Pattern;

public final class HomeAccess {
    private static final String PERM_PREFIX = "dorkcore.homes.";
    private static final String PERM_UNLIMITED = "dorkcore.homes.unlimited";
    private static final int UNLIMITED = Integer.MAX_VALUE;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private HomeAccess() {
    }

    public static int cap(Player player, int configDefault) {
        if (player.hasPermission(PERM_UNLIMITED)) {
            return UNLIMITED;
        }
        int best = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith(PERM_PREFIX)) {
                String tail = perm.substring(PERM_PREFIX.length());
                try {
                    best = Math.max(best, Integer.parseInt(tail));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best >= 0 ? best : configDefault;
    }

    public static boolean unlimited(int cap) {
        return cap == UNLIMITED;
    }

    public static boolean validName(String name, int maxLength) {
        return name != null
                && !name.isEmpty()
                && name.length() <= maxLength
                && VALID_NAME.matcher(name).matches();
    }
}
