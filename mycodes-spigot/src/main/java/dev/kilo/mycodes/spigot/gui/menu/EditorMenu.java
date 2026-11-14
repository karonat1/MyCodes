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

import java.util.List;

/**
 * Р РµРґР°РєС‚РѕСЂ РїСЂРѕРјРѕРєРѕРґР°: РєРѕРґ, РІРєР»/РІС‹РєР», СЃРµСЂРІРµСЂ, РЅР°РіСЂР°РґР°, Р»РёРјРёС‚С‹, Telegram,
 * СЌС„С„РµРєС‚, РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ, Р·Р°РґРµСЂР¶РєР°, СЃРѕРѕР±С‰РµРЅРёРµ, СЃСЂРѕРє, СЃС‚Р°С‚РёСЃС‚РёРєР°, СѓРґР°Р»РµРЅРёРµ.
 */
public class EditorMenu extends Menu {

    private final PromoCode promo;

    public EditorMenu(MyCodesSpigotPlugin plugin, Player player, PromoCode promo) {
        super(plugin, player, 54, plugin.core().lang().msg("menu-title-editor"));
        this.promo = promo;
    }

    @Override
    protected void draw() {
        fill(Material.GRAY_STAINED_GLASS_PANE, " ");

        // 4: СЃРІРѕРґРєР°
        set(4, ItemBuilder.of(promo.rewardType() == RewardType.ITEM ? Material.CHEST : Material.PAPER)
                .name(plugin.core().lang().msg("editor-info-name", "code", promo.code))
                .lore(plugin.core().lang().msgList("editor-info-lore",
                        "status", plugin.core().lang().msg(promo.enabled ? "status-enabled" : "status-disabled"),
                        "uses", String.valueOf(promo.usedCount),
                        "max", promo.isUnlimited() ? plugin.core().lang().msg("unlimited") : String.valueOf(promo.maxUses),
                        "created", plugin.formatDate(promo.createdAt),
                        "expire", promo.expiresAt > 0 ? plugin.formatDate(promo.expiresAt) : plugin.core().lang().msg("expire-never")))
                .build());

        // 10: РїРµСЂРµРёРјРµРЅРѕРІР°С‚СЊ
        set(10, ItemBuilder.of(Material.OAK_SIGN)
                .name(plugin.core().lang().msg("editor-code-name"))
                .lore(plugin.core().lang().msgList("editor-code-lore", "code", promo.code))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promptCode();
            }
        });

        // 11: РІРєР»/РІС‹РєР»
        Material enabledMaterial = promo.enabled ? Material.GLOWSTONE_DUST : Material.GUNPOWDER;
        set(11, ItemBuilder.of(enabledMaterial)
                .name(plugin.core().lang().msg("editor-enabled-name"))
                .lore(plugin.core().lang().msgList("editor-enabled-lore",
                        "status", plugin.core().lang().msg(promo.enabled ? "status-enabled" : "status-disabled")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promo.enabled = !promo.enabled;
                save();
            }
        });

        // 12: СЃРµСЂРІРµСЂ
        set(12, ItemBuilder.of(Material.COMPASS)
                .name(plugin.core().lang().msg("editor-server-name"))
                .lore(plugin.core().lang().msgList("editor-server-lore",
                        "server", promo.anyServer() ? plugin.core().lang().msg("server-any") : promo.server))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new ServersMenu(plugin, player, new ServersMenu.SelectHandler() {
                    @Override
                    public void onSelect(String server) {
                        promo.server = server;
                        save();
                    }
                }).open();
            }
        });

        // 13: РЅР°РіСЂР°РґР°
        String rewardType = plugin.core().lang().msg(promo.rewardType() == RewardType.ITEM ? "type-item" : "type-command");
        ItemStack rewardIcon;
        if (promo.rewardType() == RewardType.ITEM && promo.itemBase64 != null
                && ItemCodec.fromBase64(promo.itemBase64) != null) {
            rewardIcon = ItemCodec.fromBase64(promo.itemBase64);
        } else {
            rewardIcon = ItemBuilder.of(promo.rewardType() == RewardType.ITEM ? Material.CHEST : Material.COMMAND_BLOCK)
                    .build();
        }
        set(13, ItemBuilder.of(rewardIcon)
                .name(plugin.core().lang().msg("editor-reward-name"))
                .lore(plugin.core().lang().msgList("editor-reward-lore", "type", rewardType))
                .build(), new ClickAction() {
            @Override
            public void run() {
                if (promo.rewardType() == RewardType.ITEM) {
                    new ItemMenu(plugin, player, promo.itemBase64, new ItemMenu.ItemHandler() {
                        @Override
                        public void onSave(String base64) {
                            promo.itemBase64 = base64;
                            save();
                        }
                    }).open();
                } else {
                    promptCommand();
                }
            }
        });

        // 15: Р»РёРјРёС‚ Р°РєС‚РёРІР°С†РёР№
        set(15, ItemBuilder.of(Material.OAK_SIGN)
                .name(plugin.core().lang().msg("editor-uses-name"))
                .lore(plugin.core().lang().msgList("editor-uses-lore",
                        "max", promo.isUnlimited() ? plugin.core().lang().msg("unlimited") : String.valueOf(promo.maxUses)))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promptInt("prompt-uses", new IntConsumer() {
                    @Override
                    public void accept(int value) {
                        promo.maxUses = Math.max(0, value);
                        save();
                    }
                });
            }
        });

        // 20: РѕРґРёРЅ СЂР°Р· РЅР° РёРіСЂРѕРєР°
        set(20, ItemBuilder.of(Material.FEATHER)
                .name(plugin.core().lang().msg("editor-once-name"))
                .lore(plugin.core().lang().msgList("editor-once-lore",
                        "status", plugin.core().lang().msg(promo.oncePerPlayer ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promo.oncePerPlayer = !promo.oncePerPlayer;
                save();
            }
        });

        // 21: СЂРµР¶РёРј Telegram
        set(21, ItemBuilder.of(Material.PAPER)
                .name(plugin.core().lang().msg("editor-tgmode-name"))
                .lore(plugin.core().lang().msgList("editor-tgmode-lore",
                        "tg", plugin.core().lang().msg("tg-mode-" + normalizeTgMode(promo))))
                .build(), new ClickAction() {
            @Override
            public void run() {
                String mode = normalizeTgMode(promo);
                promo.telegramMode = mode.equals("DEFAULT") ? "REQUIRED" : mode.equals("REQUIRED") ? "OFF" : "DEFAULT";
                save();
            }
        });

        // 22: Р°РЅС‚Рё-РјСѓР»СЊС‚РёР°РєРєР°СѓРЅС‚
        set(22, ItemBuilder.of(Material.IRON_BARS)
                .name(plugin.core().lang().msg("editor-tgonce-name"))
                .lore(plugin.core().lang().msgList("editor-tgonce-lore",
                        "status", plugin.core().lang().msg(promo.telegramOnce ? "generic-on" : "generic-off")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promo.telegramOnce = !promo.telegramOnce;
                save();
            }
        });

        // 24: СЃСЂРѕРє
        set(24, ItemBuilder.of(Material.CLOCK)
                .name(plugin.core().lang().msg("editor-expiry-name"))
                .lore(plugin.core().lang().msgList("editor-expiry-lore",
                        "expire", promo.expiresAt > 0 ? plugin.formatDate(promo.expiresAt) : plugin.core().lang().msg("expire-never")))
                .build(), new ClickAction() {
            @Override
            public void run() {
                plugin.chatInput().request(player, new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String text) {
                        String t = text.toLowerCase();
                        if (t.equals("never") || t.equals("РЅРёРєРѕРіРґР°") || t.equals("0")) {
                            promo.expiresAt = 0L;
                        } else {
                            try {
                                int days = Integer.parseInt(t.trim());
                                if (days <= 0) {
                                    promo.expiresAt = 0L;
                                } else {
                                    promo.expiresAt = System.currentTimeMillis() + days * 86_400_000L;
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage(plugin.core().lang().msg("input-invalid"));
                                new EditorMenu(plugin, player, promo).open();
                                return;
                            }
                        }
                        save();
                    }
                });
                player.sendMessage(expiryPrompt());
            }
        });

        // 28: СЌС„С„РµРєС‚
        set(28, ItemBuilder.of(effectMaterial(promo.effectType()))
                .name(plugin.core().lang().msg("editor-effect-name"))
                .lore(plugin.core().lang().msgList("editor-effect-lore",
                        "effect", plugin.core().lang().msg("effect-" + promo.effectType().name().toLowerCase())))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new EffectsMenu(plugin, player, promo.effectType(), new EffectsMenu.SelectHandler() {
                    @Override
                    public void onSelect(dev.kilo.mycodes.common.model.EffectType type) {
                        promo.effect = type.name();
                        save();
                    }
                }, promo).open();
            }
        });

        // 29: РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ СЌС„С„РµРєС‚Р°
        set(29, ItemBuilder.of(Material.DAYLIGHT_DETECTOR)
                .name(plugin.core().lang().msg("editor-duration-name"))
                .lore(plugin.core().lang().msgList("editor-duration-lore",
                        "duration", String.valueOf(promo.effectDuration)))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promptInt("prompt-duration", new IntConsumer() {
                    @Override
                    public void accept(int value) {
                        promo.effectDuration = Math.max(1, Math.min(10, value));
                        save();
                    }
                });
            }
        });

        // 30: Р·Р°РґРµСЂР¶РєР° РЅР°РіСЂР°РґС‹ (В«РІСЂРµРјСЏ Р°РєС‚РёРІР°С†РёРёВ»)
        set(30, ItemBuilder.of(Material.REPEATER)
                .name(plugin.core().lang().msg("editor-delay-name"))
                .lore(plugin.core().lang().msgList("editor-delay-lore",
                        "delay", String.valueOf(promo.rewardDelay)))
                .build(), new ClickAction() {
            @Override
            public void run() {
                promptInt("prompt-delay", new IntConsumer() {
                    @Override
                    public void accept(int value) {
                        promo.rewardDelay = Math.max(0, Math.min(60, value));
                        save();
                    }
                });
            }
        });

        // 31: СЃРѕРѕР±С‰РµРЅРёРµ
        String custom = promo.message == null || promo.message.isEmpty()
                ? plugin.core().lang().msg("generic-none") : "&a" + firstLine(promo.message);
        set(31, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(plugin.core().lang().msg("editor-message-name"))
                .lore(plugin.core().lang().msgList("editor-message-lore", "custom", custom))
                .build(), new ClickAction() {
            @Override
            public void run() {
                plugin.chatInput().request(player, new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String text) {
                        if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("очистить")) {
                            promo.message = null;
                        } else {
                            promo.message = text;
                        }
                        save();
                    }
                });
                player.sendMessage(plugin.core().lang().msg("prompt-message",
                        "cancel", plugin.core().lang().plain("cancel-word"),
                        "code", promo.code));
            }
        });

        // 37: РїСЂРµРІСЊСЋ СЌС„С„РµРєС‚Р°
        set(37, ItemBuilder.of(effectMaterial(promo.effectType()))
                .name(plugin.core().lang().msg("editor-preview-name"))
                .lore(plugin.core().lang().msgList("editor-preview-lore"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                plugin.playEffectPreview(player, promo.effectType(), promo.effectDuration);
            }
        });

        // 40: СЃС‚Р°С‚РёСЃС‚РёРєР°
        set(40, ItemBuilder.of(Material.BOOK)
                .name(plugin.core().lang().msg("editor-stats-name"))
                .lore(plugin.core().lang().msgList("editor-stats-lore",
                        "uses", String.valueOf(promo.usedCount),
                        "max", promo.isUnlimited() ? plugin.core().lang().msg("unlimited") : String.valueOf(promo.maxUses)))
                .build());

        // 49: РЅР°Р·Р°Рґ
        set(49, ItemBuilder.of(Material.ARROW)
                .name(plugin.core().lang().msg("editor-back-name"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                new ListMenu(plugin, player, 1).open();
            }
        });

        // 50: СѓРґР°Р»РµРЅРёРµ
        if (player.hasPermission("mycodes.delete")) {
            set(50, ItemBuilder.of(Material.TNT)
                    .name(plugin.core().lang().msg("editor-delete-name"))
                    .lore(plugin.core().lang().msgList("editor-delete-lore"))
                    .build(), new ClickAction() {
                @Override
                public void run() {
                    new ConfirmMenu(plugin, player, promo, new ConfirmMenu.ConfirmHandler() {
                        @Override
                        public void onConfirm() {
                            plugin.backend().delete(promo.id, promo.code, new java.util.function.Consumer<String>() {
                                @Override
                                public void accept(String message) {
                                    player.sendMessage(message);
                                    new ListMenu(plugin, player, 1).open();
                                }
                            });
                        }
                    }).open();
                }
            });
        }

        // 53: Р·Р°РєСЂС‹С‚СЊ
        set(53, ItemBuilder.of(Material.BARRIER)
                .name(plugin.core().lang().msg("close-name"))
                .build(), new ClickAction() {
            @Override
            public void run() {
                player.closeInventory();
            }
        });
    }

    private String normalizeTgMode(PromoCode promo) {
        if (promo.telegramMode == null) return "DEFAULT";
        String m = promo.telegramMode.toUpperCase();
        if (!m.equals("REQUIRED") && !m.equals("OFF")) return "DEFAULT";
        return m;
    }

    private String firstLine(String message) {
        int i = message.indexOf('\n');
        return i < 0 ? message : message.substring(0, i);
    }

    private void promptCode() {
        plugin.chatInput().request(player, new java.util.function.Consumer<String>() {
            @Override
            public void accept(String text) {
                String code = text.toUpperCase().trim();
                if (!code.matches("^[A-Z0-9_\\-]{3,32}$")) {
                    player.sendMessage(plugin.core().lang().msg("create-invalid-code"));
                } else {
                    promo.code = code;
                }
                save();
            }
        });
        player.sendMessage(plugin.core().lang().msg("prompt-code",
                "cancel", plugin.core().lang().plain("cancel-word")));
    }

    private void promptCommand() {
        plugin.chatInput().request(player, new java.util.function.Consumer<String>() {
            @Override
            public void accept(String text) {
                promo.command = text.startsWith("/") ? text.substring(1) : text;
                save();
            }
        });
        player.sendMessage(plugin.core().lang().msg("create-prompt-command",
                "cancel", plugin.core().lang().plain("cancel-word")));
    }

    private String expiryPrompt() {
        return plugin.core().lang().msg("prompt-expiry",
                "cancel", plugin.core().lang().plain("cancel-word"));
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private void promptInt(String promptKey, final IntConsumer consumer) {
        plugin.chatInput().request(player, new java.util.function.Consumer<String>() {
            @Override
            public void accept(String text) {
                try {
                    int value = Integer.parseInt(text.trim());
                    consumer.accept(value); // потребитель сам вызывает save()
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.core().lang().msg("input-invalid"));
                    save();
                }
            }
        });
        player.sendMessage(plugin.core().lang().msg(promptKey,
                "cancel", plugin.core().lang().plain("cancel-word")));
    }

    private void save() {
        plugin.saveEdit(player, promo);
    }

    public static Material effectMaterial(dev.kilo.mycodes.common.model.EffectType type) {
        switch (type) {
            case FIREWORK: return Material.FIREWORK_ROCKET;
            case FIREWORK_RAIN: return Material.FIREWORK_STAR;
            case HELIX: return Material.BLAZE_ROD;
            case DOME: return Material.BEACON;
            case TOTEM: return Material.TOTEM_OF_UNDYING;
            case HEARTS: return Material.POPPY;
            case ENCHANT: return Material.ENCHANTING_TABLE;
            case EXPLOSION: return Material.TNT;
            case WATER: return Material.WATER_BUCKET;
            case MYSTIC: return Material.END_ROD;
            case LASER: return Material.SEA_LANTERN;
            case FAIRY: return Material.SUGAR;
            case DRAGON: return Material.DRAGON_HEAD;
            case PULSAR: return Material.NETHER_STAR;
            default: return Material.FIREWORK_ROCKET;
        }
    }
}

