package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.backend.Backend;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import dev.kilo.mycodes.spigot.util.ItemCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Главное меню /mcd menu: список промокодов, создание, настройки, помощь.
 */
public class MainMenu extends Menu {

    public MainMenu(MyCodesSpigotPlugin plugin, Player player) {
        super(plugin, player, 27, plugin.core().lang().msg("menu-title-main"));
    }

    @Override
    protected void draw() {
        Backend backend = plugin.backend();
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        // Список промокодов
        ItemStack list = ItemBuilder.of(Material.BOOK)
                .name(plugin.core().lang().msg("main-list-name"))
                .lore(plugin.core().lang().msgList("main-list-lore"))
                .build();
        set(11, list, new ClickAction() {
            @Override
            public void run() {
                new ListMenu(plugin, player, 1).open();
            }
        });

        // Создание промокода
        if (player.hasPermission("mycodes.create")) {
            ItemStack create = ItemBuilder.of(Material.ANVIL)
                    .name(plugin.core().lang().msg("main-create-name"))
                    .lore(plugin.core().lang().msgList("main-create-lore"))
                    .build();
            set(13, create, new ClickAction() {
                @Override
                public void run() {
                    plugin.startWizard(player);
                }
            });
        }

        // Настройки
        if (player.hasPermission("mycodes.edit")) {
            ItemStack settings = ItemBuilder.of(Material.REPEATER)
                    .name(plugin.core().lang().msg("main-settings-name"))
                    .lore(plugin.core().lang().msgList("main-settings-lore"))
                    .build();
            set(15, settings, new ClickAction() {
                @Override
                public void run() {
                    new SettingsMenu(plugin, player).open();
                }
            });

            // Режим работы (сеть / одиночный сервер)
            ItemStack mode;
            if (backend.isRemote()) {
                mode = ItemBuilder.of(Material.ENDER_PEARL)
                        .name(plugin.core().lang().msg("mode-bridge-name"))
                        .lore(plugin.core().lang().msgList("mode-bridge-lore",
                                "proxy", backend.platformLabel()))
                        .build();
            } else {
                mode = ItemBuilder.of(Material.NETHER_STAR)
                        .name(plugin.core().lang().msg("mode-standalone-name"))
                        .lore(plugin.core().lang().msgList("mode-standalone-lore"))
                        .build();
            }
            set(4, mode);
        } else {
            ItemStack mode = backend.isRemote()
                    ? ItemBuilder.of(Material.ENDER_PEARL).name(
                            plugin.core().lang().msg("mode-bridge-name")).build()
                    : ItemBuilder.of(Material.NETHER_STAR).name(
                            plugin.core().lang().msg("mode-standalone-name")).build();
            set(4, mode);
        }

        // Помощь
        ItemStack help = ItemBuilder.of(Material.FILLED_MAP)
                .name(plugin.core().lang().msg("main-help-name"))
                .lore(plugin.core().lang().msgList("main-help-lore"))
                .build();
        set(22, help, new ClickAction() {
            @Override
            public void run() {
                player.closeInventory();
                for (String line : plugin.core().lang().msgList("help")) {
                    player.sendMessage(line);
                }
            }
        });

        ItemStack close = ItemBuilder.of(Material.BARRIER)
                .name(plugin.core().lang().msg("close-name"))
                .build();
        set(26, close, new ClickAction() {
            @Override
            public void run() {
                player.closeInventory();
            }
        });
    }
}
