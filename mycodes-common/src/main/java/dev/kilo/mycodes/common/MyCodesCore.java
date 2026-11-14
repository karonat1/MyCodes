package dev.kilo.mycodes.common;

import dev.kilo.mycodes.common.config.ConfigManager;
import dev.kilo.mycodes.common.lang.Lang;
import dev.kilo.mycodes.common.promo.PromoManager;
import dev.kilo.mycodes.common.storage.JsonStorage;
import dev.kilo.mycodes.common.storage.MySqlStorage;
import dev.kilo.mycodes.common.storage.Storage;
import dev.kilo.mycodes.common.tg.TelegramBot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ядро MyCodes, общее для всех платформ (BungeeCord/Waterfall, Velocity, Spigot).
 * Владеет конфигом, языками, хранилищем, промокодами и Telegram-ботом.
 */
public class MyCodesCore {

    private final PlatformAdapter adapter;
    private final ConfigManager config;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private Lang lang;
    private Storage storage;
    private PromoManager promos;
    private TelegramBot telegramBot;
    private volatile boolean enabled;

    public MyCodesCore(PlatformAdapter adapter) {
        this.adapter = adapter;
        this.config = new ConfigManager(adapter, adapter.dataFolder());
        this.executor = Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "MyCodes-Worker");
                t.setDaemon(true);
                return t;
            }
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "MyCodes-Scheduler");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void enable() {
        try {
            config.load();
        } catch (Exception e) {
            adapter.logError("Failed to load config.yml", e);
        }
        try {
            lang = new Lang(adapter, adapter.dataFolder(), config.language());
            lang.load(config.language());
        } catch (Exception e) {
            adapter.logError("Failed to load language files", e);
            try {
                lang = new Lang(adapter, adapter.dataFolder(), "en");
                lang.load("en");
            } catch (Exception ignored) {
            }
        }
        openStorage();
        promos = new PromoManager(this);
        promos.loadFromStorage();
        startTelegram();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    storage.cleanExpiredPendingLinks(System.currentTimeMillis());
                } catch (Exception ignored) {
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
        enabled = true;
        adapter.logInfo("MyCodes core enabled: language=" + config.language()
                + ", storage=" + (storage instanceof MySqlStorage ? "MySQL" : "JSON files"));
    }

    public void reload() {
        stopTelegram();
        try {
            config.load();
        } catch (Exception e) {
            adapter.logError("Failed to reload config.yml", e);
        }
        try {
            if (lang != null && lang.code().equals(config.language())) {
                lang.load(config.language());
            } else {
                lang = new Lang(adapter, adapter.dataFolder(), config.language());
                lang.load(config.language());
            }
        } catch (Exception e) {
            adapter.logError("Failed to reload language files", e);
        }
        try {
            storage.close();
        } catch (Exception ignored) {
        }
        openStorage();
        if (promos == null) promos = new PromoManager(this);
        promos.loadFromStorage();
        startTelegram();
        adapter.logInfo("MyCodes reloaded (language: " + config.language() + ").");
    }

    public void shutdown() {
        enabled = false;
        stopTelegram();
        scheduler.shutdownNow();
        executor.shutdownNow();
        try {
            if (storage != null) storage.close();
        } catch (Exception ignored) {
        }
    }

    /** Экстренно выключить локального Telegram-бота
     * (используется backend-мостом в сети — иначе конфликт long-polling с прокси). */
    public void disableLocalTelegram() {
        stopTelegram();
    }

    private void openStorage() {
        if ("mysql".equalsIgnoreCase(config.storageType())) {
            try {
                MySqlStorage mysql = new MySqlStorage(
                        config.mysqlHost(), config.mysqlPort(), config.mysqlDatabase(),
                        config.mysqlUser(), config.mysqlPassword(),
                        config.mysqlPrefix(), config.mysqlSsl());
                mysql.init();
                storage = mysql;
            } catch (Exception e) {
                adapter.logError("MySQL storage failed, falling back to JSON files", e);
                storage = new JsonStorage(adapter.dataFolder());
                try {
                    storage.init();
                } catch (Exception ignored) {
                }
            }
        } else {
            storage = new JsonStorage(adapter.dataFolder());
            try {
                storage.init();
            } catch (Exception e) {
                adapter.logError("JSON storage init failed", e);
            }
        }
    }

    private void startTelegram() {
        if (!config.telegramEnabled() || config.telegramToken().isEmpty()) {
            if (config.telegramEnabled()) {
                adapter.logWarn("telegram.enabled = true, but bot-token is empty. See comments at telegram.bot-token in config.yml.");
            }
            return;
        }
        telegramBot = new TelegramBot(config.telegramToken(), config.telegramPollTimeout(),
                new CoreBotListener(), new TelegramBot.Log() {
                    @Override
                    public void info(String s) {
                        adapter.logInfo(s);
                    }

                    @Override
                    public void warn(String s) {
                        adapter.logWarn(s);
                    }

                    @Override
                    public void error(String s) {
                        adapter.logError(s, null);
                    }
                });
        if (!telegramBot.start()) {
            telegramBot = null;
            if (config.telegramRequireConfirmation()) {
                adapter.logWarn("Telegram confirmation is enabled globally, but the bot is unavailable! Promos requiring Telegram will decline until fixed.");
            }
        }
    }

    private void stopTelegram() {
        if (telegramBot != null) {
            telegramBot.stop();
            telegramBot = null;
        }
    }

    private class CoreBotListener implements TelegramBot.Listener {
        @Override
        public String onConfirm(String token, long chatId) {
            final PromoManager.TgConfirm confirm = promos.confirmTelegram(token, chatId);
            if (confirm.reply == null) return null;
            if (confirm.reward != null && confirm.playerId != null) {
                adapter.async().execute(new Runnable() {
                    @Override
                    public void run() {
                        deliverTelegramReward(confirm);
                    }
                });
            }
            return confirm.reply;
        }

        @Override
        public String onCommand(long chatId, String text) {
            String cmd = text.toLowerCase();
            if (cmd.startsWith("/chatid") || cmd.startsWith("/id")) {
                return lang.plain("tg-chatid", "id", String.valueOf(chatId));
            }
            return lang.plain("tg-greeting");
        }
    }

    // выдача награды после Telegram-подтверждения: если игрок онлайн — сразу
    // с эффектом; иначе остаётся в outstanding и выдаётся при следующем входе
    private RewardDispatcher rewardDispatcher;

    public void setRewardDispatcher(RewardDispatcher dispatcher) {
        this.rewardDispatcher = dispatcher;
    }

    private void deliverTelegramReward(final PromoManager.TgConfirm confirm) {
        if (rewardDispatcher == null) return;
        final java.util.UUID playerId;
        try {
            playerId = java.util.UUID.fromString(confirm.playerId);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (rewardDispatcher.dispatch(playerId, confirm.playerName, null, confirm.reward)) {
            promos.markRewardedById(confirm.activationId);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PlatformAdapter adapter() {
        return adapter;
    }

    public ConfigManager config() {
        return config;
    }

    public Lang lang() {
        return lang;
    }

    public Storage storage() {
        return storage;
    }

    public PromoManager promos() {
        return promos;
    }

    public TelegramBot telegramBot() {
        return telegramBot;
    }

    public ExecutorService executor() {
        return executor;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }
}
