package dev.kilo.mycodes.spigot.backend;

import dev.kilo.mycodes.common.MyCodesCore;
import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.common.settings.SettingsPatch;
import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Одиночный режим: всё хранится и активируется на этом сервере.
 * Это режим по умолчанию, если MyCodes не найден на прокси.
 */
public class LocalBackend implements Backend {

    private final MyCodesSpigotPlugin plugin;

    public LocalBackend(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    private MyCodesCore core() {
        return plugin.core();
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String platformLabel() {
        return core().adapter().platformName();
    }

    @Override
    public List<PromoCode> promos() {
        return core().promos().all();
    }

    @Override
    public List<String> servers() {
        return core().adapter().serverNames();
    }

    @Override
    public String language() {
        return core().config().language();
    }

    @Override
    public EffectType defaultEffect() {
        return core().config().defaultEffect();
    }

    @Override
    public int defaultEffectDuration() {
        return core().config().defaultEffectDuration();
    }

    @Override
    public int defaultRewardDelay() {
        return core().config().defaultRewardDelay();
    }

    @Override
    public boolean effectSounds() {
        return core().config().effectSounds();
    }

    @Override
    public boolean broadcastOnCreate() {
        return core().config().broadcastOnCreate();
    }

    @Override
    public boolean telegramEnabled() {
        return core().config().telegramEnabled();
    }

    @Override
    public boolean telegramConnected() {
        return core().telegramBot() != null && core().telegramBot().isAvailable();
    }

    @Override
    public String telegramUsername() {
        return core().telegramBot() != null ? core().telegramBot().username() : null;
    }

    @Override
    public boolean telegramRequire() {
        return core().config().telegramRequireConfirmation();
    }

    @Override
    public boolean telegramAntiMulti() {
        return core().config().telegramAntiMulti();
    }

    @Override
    public boolean telegramNotify() {
        return core().config().telegramNotifyOnCreate();
    }

    @Override
    public String telegramChatId() {
        return core().config().telegramToken();
    }

    @Override
    public void refreshAsync() {
        // локально данные всегда свежие
    }

    @Override
    public void create(final PromoCode promo, final Consumer<String> feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final PromoManagerCreateResult result = createSync(promo);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        feedback.accept(result.message);
                    }
                });
            }
        });
    }

    private static class PromoManagerCreateResult {
        String message;
    }

    private PromoManagerCreateResult createSync(PromoCode promo) {
        PromoManagerCreateResult out = new PromoManagerCreateResult();
        out.message = core().promos().create(promo).message;
        return out;
    }

    @Override
    public void update(final PromoCode promo, final Consumer<String> feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final boolean ok = core().promos().update(promo);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        feedback.accept(ok
                                ? core().lang().msg("editor-updated")
                                : core().lang().msg("storage-error"));
                    }
                });
            }
        });
    }

    @Override
    public void delete(final String promoId, final String code, final Consumer<String> feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final boolean ok = core().promos().delete(promoId);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        feedback.accept(ok
                                ? core().lang().msg("promo-deleted", "code", code)
                                : core().lang().msg("storage-error"));
                    }
                });
            }
        });
    }

    @Override
    public void updateGeneral(final GeneralSettingsUpdate update, final Consumer<String> feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    SettingsPatch.applyGeneral(
                            plugin.core().adapter().dataFolder().resolve("config.yml"), update);
                    plugin.core().reload();
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String err = error;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        feedback.accept(err == null
                                ? core().lang().msg("settings-done")
                                : core().lang().msg("storage-error"));
                    }
                });
            }
        });
    }

    @Override
    public void updateTelegram(final TelegramSettingsUpdate update, final Consumer<String> feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    SettingsPatch.applyTelegram(
                            plugin.core().adapter().dataFolder().resolve("config.yml"), update);
                    plugin.core().reload();
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String err = error;
                final boolean connected = telegramConnected();
                final String username = telegramUsername();
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (err != null) {
                            feedback.accept(core().lang().msg("storage-error"));
                            return;
                        }
                        if (!core().config().telegramEnabled()) {
                            feedback.accept(core().lang().msg("settings-done"));
                            return;
                        }
                        feedback.accept(connected && username != null
                                ? core().lang().msg("tg-token-ok", "username", username)
                                : core().lang().msg("tg-token-fail"));
                    }
                });
            }
        });
    }

    @Override
    public void reload(final Consumer<String> feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                core().reload();
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        feedback.accept(core().lang().msg("reloaded"));
                    }
                });
            }
        });
    }

    @Override
    public void redeem(final Player player, final String code, final Consumer<RedeemResult> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final RedeemResult result = core().promos().redeem(
                        player.getUniqueId(), player.getName(), null, code);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(result);
                    }
                });
            }
        });
    }
}
