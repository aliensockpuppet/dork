package io.github.aliensockpuppet.dorkmotd.velocity;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MotdManager {
    public enum Result { OK, GLOBAL_COOLDOWN, PLAYER_COOLDOWN, INVALID, EMPTY }

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final MotdStore store;
    private int maxLength;
    private long globalCooldownMinMillis;
    private long globalCooldownMaxMillis;
    private long playerCooldownMinMillis;
    private long playerCooldownMaxMillis;
    private volatile long globalCooldownRolledMillis;
    private final java.util.Map<UUID, Long> playerCooldownRolledMillis = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile Component header = MINI.deserialize("<green>A Minecraft Server");
    private boolean quotePlayer = true;

    private volatile Component cached;

    private volatile boolean rotationEnabled;
    private boolean rotationRandom;
    private List<Component> rotationPool = List.of();
    private int rotationIndex;
    private volatile Component rotationCurrent;

    public MotdManager(MotdStore store, int maxLength,
                       long globalMinSeconds, long globalMaxSeconds,
                       long playerMinSeconds, long playerMaxSeconds) {
        this.store = store;
        reconfigure(maxLength, globalMinSeconds, globalMaxSeconds, playerMinSeconds, playerMaxSeconds);
        rebuildCache();
    }

    public synchronized void reconfigure(int maxLength,
                            long globalMinSeconds, long globalMaxSeconds,
                            long playerMinSeconds, long playerMaxSeconds) {
        this.maxLength = Math.max(1, maxLength);
        this.globalCooldownMinMillis = Math.max(0L, globalMinSeconds) * 1000L;
        this.globalCooldownMaxMillis = Math.max(globalCooldownMinMillis, globalMaxSeconds * 1000L);
        this.playerCooldownMinMillis = Math.max(0L, playerMinSeconds) * 1000L;
        this.playerCooldownMaxMillis = Math.max(playerCooldownMinMillis, playerMaxSeconds * 1000L);
        this.globalCooldownRolledMillis = globalCooldownMinMillis;
    }

    private static long roll(long minMillis, long maxMillis) {
        if (maxMillis <= minMillis) {
            return minMillis;
        }
        return ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1L);
    }

    public synchronized void configureFormat(String headerRaw, boolean quotePlayer) {
        try {
            this.header = MINI.deserialize(headerRaw == null || headerRaw.isBlank()
                    ? "<green>A Minecraft Server" : headerRaw);
        } catch (RuntimeException e) {
            this.header = MINI.deserialize("<green>A Minecraft Server");
        }
        this.quotePlayer = quotePlayer;
    }

    public Component current() {
        Component player = cached;
        if (player != null) {
            return player;
        }
        if (rotationEnabled && rotationCurrent != null) {
            return rotationCurrent;
        }
        return header;
    }

    private Component compose(Component line2, boolean quote) {
        Component second = line2;
        if (quote) {
            Component q = Component.text("\"", NamedTextColor.GRAY);
            second = Component.empty().append(q).append(line2).append(q);
        }
        return Component.empty().append(header).append(Component.newline()).append(second);
    }

    public synchronized void configureRotation(boolean enabled, boolean random, List<String> raw) {
        this.rotationEnabled = enabled;
        this.rotationRandom = random;
        List<Component> pool = new ArrayList<>();
        if (raw != null) {
            for (String line : raw) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                try {
                    pool.add(MINI.deserialize(line));
                } catch (RuntimeException ignored) {
                }
            }
        }
        this.rotationPool = pool;
        this.rotationIndex = 0;
        this.rotationCurrent = pool.isEmpty() ? null : compose(pool.get(0), false);
    }

    public boolean rotationActive() {
        return rotationEnabled && !rotationPool.isEmpty();
    }

    public synchronized void advanceRotation() {
        if (!rotationActive() || cached != null) {
            return;
        }
        Component entry;
        if (rotationRandom) {
            entry = rotationPool.get(ThreadLocalRandom.current().nextInt(rotationPool.size()));
        } else {
            entry = rotationPool.get(rotationIndex);
            rotationIndex = (rotationIndex + 1) % rotationPool.size();
        }
        this.rotationCurrent = compose(entry, false);
    }

    public long globalCooldownLeft(long nowMillis) {
        long left = store.lastGlobalMillis() + globalCooldownRolledMillis - nowMillis;
        return left <= 0 ? 0 : (left + 999L) / 1000L;
    }

    public long playerCooldownLeft(UUID player, long nowMillis) {
        long rolled = playerCooldownRolledMillis.getOrDefault(player, playerCooldownMinMillis);
        long left = store.lastPlayerMillis(player) + rolled - nowMillis;
        return left <= 0 ? 0 : (left + 999L) / 1000L;
    }

    public synchronized Result attemptSet(Player player, String rawInput, long nowMillis) {
        if (bypasses(player)) {
            return applySet(player.getUniqueId(), rawInput, nowMillis);
        }
        if (globalCooldownLeft(nowMillis) > 0) {
            return Result.GLOBAL_COOLDOWN;
        }
        if (playerCooldownLeft(player.getUniqueId(), nowMillis) > 0) {
            return Result.PLAYER_COOLDOWN;
        }
        return applySet(player.getUniqueId(), rawInput, nowMillis);
    }

    private Result applySet(UUID player, String rawInput, long nowMillis) {
        String clean = sanitize(rawInput);
        if (clean.isEmpty()) {
            return Result.EMPTY;
        }
        try {
            MINI.deserialize(clean);
        } catch (RuntimeException e) {
            return Result.INVALID;
        }
        store.recordChange(clean, player, nowMillis);
        this.globalCooldownRolledMillis = roll(globalCooldownMinMillis, globalCooldownMaxMillis);
        playerCooldownRolledMillis.put(player, roll(playerCooldownMinMillis, playerCooldownMaxMillis));
        rebuildCache();
        return Result.OK;
    }

    public boolean bypasses(Player player) {
        return player.hasPermission("dorkmotd.bypass");
    }

    private static final java.util.regex.Pattern INTERACTIVE_TAGS =
            java.util.regex.Pattern.compile("</?(?:click|hover|insert|insertion)(?::[^<>]*)?>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw.replace("§", "");
        out = convertAmpersandCodes(out);
        StringBuilder sb = new StringBuilder(out.length());
        for (int i = 0; i < out.length(); i++) {
            char ch = out.charAt(i);
            sb.append(ch < ' ' ? ' ' : ch);
        }
        out = sb.toString().replaceAll("\\s+", " ").strip();
        for (int pass = 0; pass < 8; pass++) {
            String stripped = INTERACTIVE_TAGS.matcher(out).replaceAll("");
            if (stripped.equals(out)) {
                break;
            }
            out = stripped;
        }
        if (out.length() > maxLength) {
            out = out.substring(0, maxLength);
        }
        return out;
    }

    private static String convertAmpersandCodes(String in) {
        if (in.indexOf('&') < 0) {
            return in;
        }
        StringBuilder sb = new StringBuilder(in.length() + 16);
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch != '&' || i + 1 >= in.length()) {
                sb.append(ch);
                continue;
            }
            char code = Character.toLowerCase(in.charAt(i + 1));
            if (code == '&') {
                sb.append('&');
                i++;
                continue;
            }
            String tag = legacyTag(code);
            if (tag != null) {
                sb.append(tag);
                i++;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String legacyTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    public synchronized void rebuildCache() {
        String raw = store.motd();
        if (raw == null || raw.isBlank()) {
            this.cached = null;
            return;
        }
        try {
            this.cached = compose(MINI.deserialize(raw), quotePlayer);
        } catch (RuntimeException e) {
            this.cached = null;
        }
    }
}
