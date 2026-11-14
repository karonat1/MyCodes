package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RewardType;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import dev.kilo.mycodes.spigot.util.ItemCodec;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Список промокодов с пагинацией. ЛКМ по коду — редактор.
 */
public class ListMenu extends Menu {

    private static final int PAGE_SIZE = 45;
    private final int page;

    public ListMenu(MyCodesSpigotPlugin plugin, Player player, int page) {
        super(plugin, player, 54, plugin.core().lang().msg("menu-title-list"));
        this.page = page;
    }

    @Override
    protected void draw() {
        List<PromoCode> promos = plugin.backend().promos();
        int pages = Math.max(1, (promos.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(1, page), pages);
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(promos.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            final PromoCode promo = promos.get(i);
            int slot = i - from;
            Material material = promo.rewardType() == RewardType.ITEM ? Material.CHEST : Material.PAPER;
            String nameKey = promo.rewardType() == RewardType.ITEM
                    ? "list-promo-item-name" : "list-promo-command-name";
            ItemBuilder builder = ItemBuilder.of(material)
                    .name(plugin.core().lang().msg(nameKey, "code", promo.code))
                    .lore(promoLore(promo));
            if (!promo.enabled) {
                builder.glow();
            }
            set(slot, builder.build(), new ClickAction() {
                @Override
                public void run() {
                    if (player.hasPermission("mycodes.edit")) {
                        plugin.beginEdit(player, promo);
                        new EditorMenu(plugin, player, promo).open();
                    }
                }
            });
        }
        for (int slot = to - from; slot < PAGE_SIZE; slot++) {
            set(slot, ItemBuilder.of(Material.AIR).build());
        }

        if (current > 1) {
            set(45, ItemBuilder.of(Material.ARROW)
                            .name(plugin.core().lang().msg("list-prev-name")).build(),
                    new ClickAction() {
                        @Override
                        public void run() {
                            new ListMenu(plugin, player, current - 1).open();
                        }
                    });
        }
        if (current < pages) {
            set(53, ItemBuilder.of(Material.ARROW)
                            .name(plugin.core().lang().msg("list-next-name")).build(),
                    new ClickAction() {
                        @Override
                        public void run() {
                            new ListMenu(plugin, player, current + 1).open();
                        }
                    });
        }
        set(49, ItemBuilder.of(Material.BARRIER).name(plugin.core().lang().msg("close-name")).build(),
                new ClickAction() {
                    @Override
                    public void run() {
                        new MainMenu(plugin, player).open();
                    }
                });
        set(48, ItemBuilder.of(Material.EMERALD)
                        .name(plugin.core().lang().msg("main-create-name"))
                        .amount(1).glow().build(),
                new ClickAction() {
                    @Override
                    public void run() {
                        if (player.hasPermission("mycodes.create")) {
                            plugin.startWizard(player);
                        }
                    }
                });
    }

    /** Локализованный лор промокода */
    public List<String> promoLore(PromoCode promo) {
        String status = plugin.core().lang().msg(promo.enabled ? "status-enabled" : "status-disabled");
        String type = plugin.core().lang().msg(promo.rewardType() == RewardType.ITEM ? "type-item" : "type-command");
        String server = promo.anyServer()
                ? plugin.core().lang().msg("server-any") : promo.server;
        String uses = String.valueOf(promo.usedCount);
        String max = promo.isUnlimited()
                ? plugin.core().lang().msg("unlimited") : String.valueOf(promo.maxUses);
        String effect = plugin.core().lang().msg("effect-" + promo.effectType().name().toLowerCase());
        String expire = promo.expiresAt > 0
                ? plugin.core().promos().formatDate(promo.expiresAt)
                : plugin.core().lang().msg("expire-never");
        String tg = plugin.core().lang().msg("tg-mode-"
                + (promo.telegramMode == null ? "DEFAULT" : promo.telegramMode.toUpperCase()));
        return new ArrayList<String>(plugin.core().lang().msgList("list-promo-lore",
                "status", status,
                "type", type,
                "server", server,
                "uses", uses,
                "max", max,
                "effect", effect,
                "duration", String.valueOf(promo.effectDuration),
                "delay", String.valueOf(promo.rewardDelay),
                "tg", tg,
                "expire", expire));
    }
}
