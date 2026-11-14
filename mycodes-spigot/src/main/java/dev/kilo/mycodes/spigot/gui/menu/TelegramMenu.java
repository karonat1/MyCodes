package dev.kilo.mycodes.spigot.gui.menu;

import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import dev.kilo.mycodes.spigot.backend.Backend;
import dev.kilo.mycodes.spigot.gui.ItemBuilder;
import dev.kilo.mycodes.spigot.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * РќР°СЃС‚СЂРѕР№РєРё Telegram-Р±РѕС‚Р°: С‚РѕРєРµРЅ (РєР»СЋС‡), Р°РґРјРёРЅСЃРєРёР№ С‡Р°С‚, РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ,
 * Р°РЅС‚Рё-РјСѓР»СЊС‚РёР°РєРєР°СѓРЅС‚, СѓРІРµРґРѕРјР»РµРЅРёСЏ. РЎРј. РєРѕРјРјРµРЅС‚Р°СЂРёРё РІ config.yml.
 */
public class TelegramMenu extends Menu {

    public TelegramMenu(MyCodesSpigotPlugin plugin, Player player) {
        super(plugin, player, 45, plugin.core().lang().msg("menu-title-telegram"));
    }

    private String statusLine(Backend backend) {
        if (!backend.telegramEnabled()) {
            return plugin.core().lang().msg("tg-status-off");
        }
        String token = backend.isRemote() ? "unknown" : plugin.core().config().telegramToken();
        if (token == null || token.isEmpty()) {
            return plugin.core().lang().msg("tg-status-no-token");
        }
        if (backend.telegramConnected()) {
            return plugin.core().lang().msg("tg-status-connected",
                    "username", backend.telegramUsername() == null ? "" : backend.telegramUsername());
        }
        return plugin.core().lang().msg("tg-status-unavailable");
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");
        final Backend backend = plugin.backend();

        // 4: СЃС‚Р°С‚СѓСЃ Р±РѕС‚Р°
        set(4, ItemBuilder.of(Material.PAPER)
                .name(plugin.core().lang().msg("settings-telegram-name"))
                .lore(plugin.core().lang().msgList("tg-settings-status-lore",
                        "status", statusLine(backend)))
                .build());

        // 10: РІРєР»/РІС‹РєР»
        set(10, ItemBuilder.of(backend.telegramEnabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(plugin.core().lang().msg("tg-enabled-name"))
                .lore(plugin.core().lang().msgList("tg-enabled-lore",
                        "status", plugin.core().lang().msg(backend.telegramEnabled() ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                TelegramSettingsUpdate update = new TelegramSettingsUpdate();
                update.enabled = !backend.telegramEnabled();
                apply(update);
            }
        });

        // 12: С‚РѕРєРµРЅ (РєР»СЋС‡ Р±РѕС‚Р°)
        set(12, ItemBuilder.of(Material.TRIPWIRE_HOOK)
                .name(plugin.core().lang().msg("tg-token-name"))
                .lore(tokenLore(backend))
                .build(), new ClickAction() {
            @Override
            public void run() {
                player.sendMessage(plugin.core().lang().msg("tg-token-prompt",
                        "cancel", plugin.core().lang().plain("cancel-word")));
                plugin.chatInput().request(player, new Consumer<String>() {
                    @Override
                    public void accept(String token) {
                        TelegramSettingsUpdate update = new TelegramSettingsUpdate();
                        update.token = token.trim();
                        apply(update);
                    }
                });
            }
        });

        // 14: Р°РґРјРёРЅСЃРєРёР№ С‡Р°С‚
        set(14, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(plugin.core().lang().msg("tg-chat-name"))
                .lore(plugin.core().lang().msgList("tg-chat-lore",
                        "chat-id", backend.telegramChatId().isEmpty() ? "-" : backend.telegramChatId()))
                .build(), new ClickAction() {
            @Override
            public void run() {
                player.sendMessage(plugin.core().lang().msg("tg-chat-prompt",
                        "cancel", plugin.core().lang().plain("cancel-word")));
                plugin.chatInput().request(player, new Consumer<String>() {
                    @Override
                    public void accept(String chatId) {
                        TelegramSettingsUpdate update = new TelegramSettingsUpdate();
                        update.chatId = chatId.trim();
                        apply(update);
                    }
                });
            }
        });

        // 16: РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ С‡РµСЂРµР· Telegram
        set(16, ItemBuilder.of(Material.PAPER)
                .name(plugin.core().lang().msg("tg-require-name"))
                .lore(plugin.core().lang().msgList("tg-require-lore",
                        "status", plugin.core().lang().msg(backend.telegramRequire() ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                TelegramSettingsUpdate update = new TelegramSettingsUpdate();
                update.require = !backend.telegramRequire();
                apply(update);
            }
        });

        // 21: Р°РЅС‚Рё-РјСѓР»СЊС‚Рё
        set(21, ItemBuilder.of(Material.IRON_BARS)
                .name(plugin.core().lang().msg("tg-antimulti-name"))
                .lore(plugin.core().lang().msgList("tg-antimulti-lore",
                        "status", plugin.core().lang().msg(backend.telegramAntiMulti() ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                TelegramSettingsUpdate update = new TelegramSettingsUpdate();
                update.antiMulti = !backend.telegramAntiMulti();
                apply(update);
            }
        });

        // 23: СѓРІРµРґРѕРјР»РµРЅРёСЏ
        set(23, ItemBuilder.of(Material.NOTE_BLOCK)
                .name(plugin.core().lang().msg("tg-notify-name"))
                .lore(plugin.core().lang().msgList("tg-notify-lore",
                        "status", plugin.core().lang().msg(backend.telegramNotify() ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                TelegramSettingsUpdate update = new TelegramSettingsUpdate();
                update.notify = !backend.telegramNotify();
                apply(update);
            }
        });

        // 40: РЅР°Р·Р°Рґ
        set(40, ItemBuilder.of(Material.ARROW)
                .name(plugin.core().lang().msg("tg-back-name"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new SettingsMenu(plugin, player).open();
            }
        });
    }

    private List<String> tokenLore(Backend backend) {
        String tokenStatus;
        if (!backend.telegramEnabled()) {
            tokenStatus = plugin.core().lang().msg("tg-status-off");
        } else if (backend.telegramConnected()) {
            tokenStatus = plugin.core().lang().msg("tg-status-connected",
                    "username", backend.telegramUsername() == null ? "" : backend.telegramUsername());
        } else {
            tokenStatus = plugin.core().lang().msg("tg-status-no-token");
        }
        List<String> lore = new ArrayList<String>(plugin.core().lang().msgList("tg-token-lore",
                "token-status", tokenStatus));
        return lore;
    }

    private void apply(TelegramSettingsUpdate update) {
        plugin.backend().updateTelegram(update, new Consumer<String>() {
            @Override
            public void accept(String message) {
                player.sendMessage(message);
                new TelegramMenu(plugin, player).open();
            }
        });
    }
}

