package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.common.model.RewardType;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * РњР°СЃС‚РµСЂ СЃРѕР·РґР°РЅРёСЏ: РІС‹Р±РѕСЂ С‚РёРїР° РЅР°РіСЂР°РґС‹ вЂ” РєРѕРјР°РЅРґР° РєРѕРЅСЃРѕР»Рё РёР»Рё РїСЂРµРґРјРµС‚.
 */
public class RewardTypeMenu extends Menu {

    public RewardTypeMenu(MyCodesSpigotPlugin plugin, Player player) {
        super(plugin, player, 27, plugin.core().lang().msg("menu-title-type"));
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        final MyCodesSpigotPlugin.Wizard wizard = plugin.wizard(player.getUniqueId());
        if (wizard == null) {
            player.closeInventory();
            return;
        }

        // РљРѕРјР°РЅРґР° РєРѕРЅСЃРѕР»Рё
        set(11, ItemBuilder.of(Material.PAPER)
                .name(plugin.core().lang().msg("create-type-command-name"))
                .lore(plugin.core().lang().msgList("create-type-command-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                wizard.type = RewardType.COMMAND;
                promptCommand();
            }
        });

        // РџСЂРµРґРјРµС‚
        set(15, ItemBuilder.of(Material.CHEST)
                .name(plugin.core().lang().msg("create-type-item-name"))
                .lore(plugin.core().lang().msgList("create-type-item-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                wizard.type = RewardType.ITEM;
                promptItem();
            }
        });
    }

    private void promptCommand() {
        final MyCodesSpigotPlugin.Wizard wizard = plugin.wizard(player.getUniqueId());
        player.sendMessage(plugin.core().lang().msg("create-prompt-command",
                "cancel", plugin.core().lang().plain("cancel-word")));
        plugin.chatInput().request(player, new Consumer<String>() {
            @Override
            public void accept(String text) {
                if (wizard == null) {
                    return;
                }
                wizard.command = text.startsWith("/") ? text.substring(1) : text;
                wizard.type = RewardType.COMMAND;
                player.sendMessage(plugin.core().lang().msg("create-command-set",
                        "value", wizard.command));
                plugin.continueWizard(player, wizard);
            }
        });
    }

    private void promptItem() {
        final MyCodesSpigotPlugin.Wizard wizard = plugin.wizard(player.getUniqueId());
        player.sendMessage(plugin.core().lang().msg("create-item-hint"));
        new ItemMenu(plugin, player, null, new ItemMenu.ItemHandler() {
            @Override
            public void onSave(String base64) {
                if (wizard == null) {
                    return;
                }
                wizard.itemBase64 = base64;
                wizard.type = RewardType.ITEM;
                plugin.continueWizard(player, wizard);
            }
        }).open();
    }
}

