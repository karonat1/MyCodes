package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.backend.Backend;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Настройки: язык, Telegram, эффект по умолчанию, оповещения, reload.
 */
public class SettingsMenu extends Menu {

    public SettingsMenu(MyCodesSpigotPlugin plugin, Player player) {
        super(plugin, player, 45, plugin.core().lang().msg("menu-title-settings"));
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        Backend backend = plugin.backend();

        // 4: инфо о платформе
        String storage = backend.isRemote()
                ? plugin.core().lang().msg("storage-network")
                : plugin.core().lang().msg("mysql".equalsIgnoreCase(plugin.core().config().storageType())
                ? "storage-mysql" : "storage-json");
        set(4, ItemBuilder.of(Material.PAPER)
                .name("&6MyCodes")
                .lore(plugin.core().lang().msgList("settings-info-lore",
                        "platform", backend.platformLabel(),
                        "storage", storage,
                        "language", plugin.core().lang().code(),
                        "version", "1.0.0"))
                .build());

        // 11: язык (цикл en → ru → tr)
        set(11, ItemBuilder.of(Material.BOOK)
                .name(plugin.core().lang().msg("settings-language-name"))
                .lore(plugin.core().lang().msgList("settings-language-lore",
                        "language", plugin.core().lang().code().toUpperCase()))
                .build(), new ClickAction() {
            @Override
            public void run() {
                String current = plugin.backend().language();
                String next = current.equals("en") ? "ru" : current.equals("ru") ? "tr" : "en";
                GeneralSettingsUpdate update = new GeneralSettingsUpdate();
                update.language = next;
                plugin.backend().updateGeneral(update, new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String message) {
                        player.sendMessage(message);
                        new SettingsMenu(plugin, player).open();
                    }
                });
            }
        });

        // 13: Telegram
        set(13, ItemBuilder.of(Material.PAPER)
                .name(plugin.core().lang().msg("settings-telegram-name"))
                .lore(plugin.core().lang().msgList("settings-telegram-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new TelegramMenu(plugin, player).open();
            }
        });

        // 15: эффект по умолчанию
        set(15, ItemBuilder.of(EditorMenu.effectMaterial(backend.defaultEffect()))
                .name(plugin.core().lang().msg("settings-effect-name"))
                .lore(plugin.core().lang().msgList("settings-effect-lore",
                        "effect", plugin.core().lang().msg("effect-" + backend.defaultEffect().name().toLowerCase())))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new EffectsMenu(plugin, player, backend.defaultEffect(),
                        new EffectsMenu.SelectHandler() {
                            @Override
                            public void onSelect(EffectType type) {
                                GeneralSettingsUpdate update = new GeneralSettingsUpdate();
                                update.defaultEffect = type.name();
                                plugin.backend().updateGeneral(update, new java.util.function.Consumer<String>() {
                                    @Override
                                    public void accept(String message) {
                                        player.sendMessage(message);
                                        new SettingsMenu(plugin, player).open();
                                    }
                                });
                            }
                        }, null).open();
            }
        });

        // 24: оповещение о новых промокодах
        set(24, ItemBuilder.of(Material.BEACON)
                .name(plugin.core().lang().msg("settings-broadcast-name"))
                .lore(plugin.core().lang().msgList("settings-broadcast-lore",
                        "status", plugin.core().lang().msg(backend.broadcastOnCreate() ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                GeneralSettingsUpdate update = new GeneralSettingsUpdate();
                update.broadcastOnCreate = !plugin.backend().broadcastOnCreate();
                plugin.backend().updateGeneral(update, new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String message) {
                        player.sendMessage(message);
                        new SettingsMenu(plugin, player).open();
                    }
                });
            }
        });

        // 22: reload
        set(22, ItemBuilder.of(Material.REDSTONE_BLOCK)
                .name(plugin.core().lang().msg("settings-reload-name"))
                .lore(plugin.core().lang().msgList("settings-reload-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                plugin.backend().reload(new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String message) {
                        player.sendMessage(message);
                        new SettingsMenu(plugin, player).open();
                    }
                });
            }
        });

        // 40: закрыть
        set(40, ItemBuilder.of(Material.BARRIER)
                .name(plugin.core().lang().msg("close-name"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new MainMenu(plugin, player).open();
            }
        });
    }
}
