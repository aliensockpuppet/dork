package io.github.aliensockpuppet.dorksurvey;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SurveyManager {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final ClickCallback.Options CLICK = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private final DorkSurveyPlugin plugin;
    private final DorkCoreApi core;
    private final SurveyStore store;
    private Survey survey;

    public SurveyManager(DorkSurveyPlugin plugin, DorkCoreApi core, SurveyStore store, Survey survey) {
        this.plugin = plugin;
        this.core = core;
        this.store = store;
        this.survey = survey;
    }

    public void setSurvey(Survey survey) {
        this.survey = survey;
    }

    public boolean hasCompleted(UUID player) {
        if (survey.isEmpty()) {
            return true;
        }
        Set<String> answered = store.answeredIds(player);
        return survey.firstUnanswered(answered).isEmpty();
    }

    public void openNext(Player player) {
        if (survey.isEmpty()) {
            core.messages().send(player, "survey-none");
            return;
        }
        Set<String> answered = store.answeredIds(player.getUniqueId());
        var next = survey.firstUnanswered(answered);
        if (next.isEmpty()) {
            finish(player);
            return;
        }
        showQuestion(player, next.get());
    }

    private void showQuestion(Player player, Survey.Question question) {
        String heading = plugin.getConfig().getString("dialog.title", "<green>Server survey");
        List<DialogBody> body = List.of(DialogBody.plainMessage(render(question.prompt())));
        DialogBase base = DialogBase.builder(render(heading))
                .canCloseWithEscape(true)
                .body(body)
                .build();

        List<ActionButton> buttons = new java.util.ArrayList<>();
        for (Survey.Option option : question.options()) {
            String optionId = option.id();
            String questionId = question.id();
            buttons.add(ActionButton.builder(render(option.text()))
                    .width(300)
                    .action(DialogAction.customClick((view, audience) ->
                            onAnswer(player.getUniqueId(), questionId, optionId), CLICK))
                    .build());
        }
        int columns = Math.max(1, plugin.getConfig().getInt("dialog.columns", 1));
        Dialog dialog = Dialog.create(f -> f.empty().base(base)
                .type(DialogType.multiAction(buttons).columns(columns).build()));
        player.showDialog(dialog);
    }

    private void onAnswer(UUID playerId, String questionId, String optionId) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p == null || !p.isOnline()) {
                return;
            }
            if (survey.question(questionId).isEmpty()) {
                p.closeDialog();
                return;
            }
            store.putAnswer(playerId, questionId, optionId);
            core.sounds().play(p, "success");
            openNext(p);
        });
    }

    private void finish(Player player) {
        UUID id = player.getUniqueId();
        player.closeDialog();
        if (store.rewarded(id)) {
            core.messages().send(player, "survey-already");
            return;
        }
        long needMinutes = Math.max(0L, plugin.getConfig().getLong("reward.min-playtime-minutes", 30L));
        long playedMinutes = playedMinutes(player);
        if (playedMinutes < needMinutes) {
            say(player, "survey-playtime",
                    "<gray>Thanks for answering. Play <green>%minutes%</green> more minutes, then run <green>/survey</green> to claim your reward",
                    "%minutes%", String.valueOf(needMinutes - playedMinutes));
            return;
        }
        String ipHash = store.hashIp(ipOf(player));
        if (store.ipRewarded(ipHash)) {
            store.setRewarded(id);
            say(player, "survey-ip-used",
                    "<gray>Thanks for answering. The reward was already claimed from your connection");
            return;
        }
        grantReward(player);
        store.setRewarded(id);
        store.addRewardedIp(ipHash);
        core.messages().send(player, "survey-reward");
        core.sounds().play(player, "success");
    }

    private static long playedMinutes(Player player) {
        try {
            return player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    static String ipOf(Player player) {
        java.net.InetSocketAddress addr = player.getAddress();
        return addr == null || addr.getAddress() == null ? null : addr.getAddress().getHostAddress();
    }

    private void say(Player player, String key, String fallback, String... pairs) {
        if (core.messages().has(key)) {
            core.messages().send(player, key, pairs);
        } else {
            String text = fallback;
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                text = text.replace(pairs[i], pairs[i + 1]);
            }
            player.sendMessage(render(text));
        }
        core.sounds().play(player, "error");
    }

    private void grantReward(Player player) {
        Material material = rewardMaterial();
        int amount = Math.max(1, plugin.getConfig().getInt("reward.amount", 3));
        ItemStack stack = ItemStack.of(material, amount);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (!overflow.isEmpty() && player.getWorld() != null) {
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private Material rewardMaterial() {
        String name = plugin.getConfig().getString("reward.material", "TOTEM_OF_UNDYING");
        Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(java.util.Locale.ROOT));
        if (material == null || !material.isItem() || material.isAir()) {
            plugin.getLogger().warning("reward.material '" + name + "' is not a valid item. Using TOTEM_OF_UNDYING.");
            return Material.TOTEM_OF_UNDYING;
        }
        return material;
    }

    private Component render(String miniMessage) {
        return MINI.deserialize(miniMessage == null ? "" : miniMessage);
    }
}
