package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Подтверждение удаления промокода.
 */
public class ConfirmMenu extends Menu {

    public interface ConfirmHandler {
        void onConfirm();
    }

    private final PromoCode promo;
    private final ConfirmHandler handler;

    public ConfirmMenu(MyCodesSpigotPlugin plugin, Player player, PromoCode promo, ConfirmHandler handler) {
        super(plugin, player, 27, plugin.core().lang().msg("menu-title-confirm"));
        this.promo = promo;
        this.handler = handler;
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        set(13, ItemBuilder.of(Material.PAPER)
                .name(plugin.core().lang().msg("confirm-title-name"))
                .lore(plugin.core().lang().msgList("confirm-lore", "code", promo.code))
                .build());

        set(11, ItemBuilder.of(Material.LIME_WOOL)
                .name(plugin.core().lang().msg("confirm-yes-name"))
                .lore(plugin.core().lang().msgList("confirm-yes-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                handler.onConfirm();
            }
        });
        set(15, ItemBuilder.of(Material.RED_WOOL)
                .name(plugin.core().lang().msg("confirm-no-name"))
                .lore(plugin.core().lang().msgList("confirm-no-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new EditorMenu(plugin, player, promo).open();
            }
        });
    }
}
