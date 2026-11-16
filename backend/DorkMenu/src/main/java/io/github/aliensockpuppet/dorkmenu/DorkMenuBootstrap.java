package io.github.aliensockpuppet.dorkmenu;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DorkMenuBootstrap implements PluginBootstrap {
    private static final TypedKey<io.papermc.paper.dialog.Dialog> MENU_DIALOG =
            TypedKey.create(RegistryKey.DIALOG, Key.key("dorkmenu", "menu"));

    private record Entry(String id, String label, int rgb) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("spawn", "Spawn", 0x55FF55),
            new Entry("homes", "Homes", 0x55FF55),
            new Entry("rtp", "RTP", 0x55FF55),
            new Entry("duel", "RTPQueue", 0x55FF55),
            new Entry("stats", "Stats", 0x55FF55),
            new Entry("map", "Map", 0x55FF55),
            new Entry("store", "Store", 0x55FF55),
            new Entry("discord", "Discord", 0x5865F2),
            new Entry("settings", "Settings", 0x55FF55),
            new Entry("suicide", "Suicide", 0xFF5555));

    private static YamlConfiguration readConfig(BootstrapContext context) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            Path file = context.getDataDirectory().resolve("config.yml");
            if (Files.isRegularFile(file)) {
                yaml.load(file.toFile());
            } else {
                try (InputStream in = DorkMenuBootstrap.class.getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception e) {
            context.getLogger().warn("Could not read config.yml for the ESC menu, using defaults: {}", e.toString());
        }
        return yaml;
    }

    private static Component text(YamlConfiguration cfg, String path, Component fallback) {
        String raw = cfg.getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return MiniMessage.miniMessage().deserialize(raw);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @Override
    public void bootstrap(BootstrapContext context) {
        YamlConfiguration cfg = readConfig(context);
        if (!cfg.getBoolean("pause-screen.enabled", true)) {
            return;
        }
        List<ActionButton> buttons = new java.util.ArrayList<>();
        for (Entry e : ENTRIES) {
            if (!cfg.getBoolean("buttons." + e.id() + ".enabled", true)) {
                continue;
            }
            Component label = text(cfg, "buttons." + e.id() + ".label",
                    Component.text(e.label(), net.kyori.adventure.text.format.TextColor.color(e.rgb())));
            buttons.add(ActionButton.builder(label)
                    .width(150)
                    .action(DialogAction.customClick(
                            Key.key("dorkmenu", e.id()),
                            (net.kyori.adventure.nbt.api.BinaryTagHolder) null))
                    .build());
        }
        if (buttons.isEmpty()) {
            return;
        }
        Component title = text(cfg, "menu.title", Component.text("Menu", NamedTextColor.GREEN));
        try {
            context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event ->
                    event.registry().register(MENU_DIALOG, builder -> {
                        builder.base(DialogBase.builder(title)
                                        .canCloseWithEscape(true)
                                        .pause(false)
                                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                        .build())
                                .type(DialogType.multiAction(buttons).columns(2).build());
                    }));

            context.getLifecycleManager().registerEventHandler(
                    LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event ->
                            event.registrar().addToTag(
                                    DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(MENU_DIALOG)));
        } catch (Throwable t) {
            context.getLogger().warn("Could not register the ESC menu dialog: {}", t.toString());
        }
    }
}
