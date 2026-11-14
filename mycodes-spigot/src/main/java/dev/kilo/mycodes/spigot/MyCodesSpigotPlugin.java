package dev.kilo.mycodes.spigot;

import dev.kilo.mycodes.common.MyCodesCore;
import dev.kilo.mycodes.common.PlatformSender;
import dev.kilo.mycodes.common.command.CommandService;
import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.model.RewardType;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.common.settings.SettingsPatch;
import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import dev.kilo.mycodes.common.util.TextUtil;
import dev.kilo.mycodes.spigot.backend.Backend;
import dev.kilo.mycodes.spigot.backend.LocalBackend;
import dev.kilo.mycodes.spigot.backend.RemoteBackend;
import dev.kilo.mycodes.spigot.bridge.BridgeClient;
import dev.kilo.mycodes.spigot.command.SpigotCommandHandler;
import dev.kilo.mycodes.spigot.effects.Effects;
import dev.kilo.mycodes.spigot.gui.ChatInput;
import dev.kilo.mycodes.spigot.gui.MenuListener;
import dev.kilo.mycodes.spigot.gui.menu.EditorMenu;
import dev.kilo.mycodes.spigot.gui.menu.ListMenu;
import dev.kilo.mycodes.spigot.gui.menu.RewardTypeMenu;
import dev.kilo.mycodes.spigot.gui.menu.ServersMenu;
import dev.kilo.mycodes.spigot.reward.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * MyCodes для Spigot/Paper (1.16.5 – 1.20.1).
 *
 * <p>Работает в двух режимах:
 * <ul>
 *   <li><b>Одиночный</b> — если плагин стоит только на сервере: все данные
 *       хранятся и активируются здесь (конфиг, JSON/MySQL, Telegram);</li>
 *   <li><b>Сетевой</b> — если MyCodes стоит на прокси (BungeeCord/Waterfall/
 *       Velocity): этот плагин открывает меню и выполняет эффекты/награды,
 *       а мастер-данные хранятся на прокси.</li>
 * </ul>
 */
public class MyCodesSpigotPlugin extends JavaPlugin implements Listener {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{3,32}$");

    private MyCodesCore core;
    private BridgeClient bridge;
    private RewardService rewardService;
    private LocalBackend localBackend;
    private volatile RemoteBackend remoteBackend;
    private ChatInput chatInput;
    private CommandService commandService;
    private SpigotCommandHandler commandHandler;

    private final Map<UUID, Wizard> wizards = new ConcurrentHashMap<UUID, Wizard>();
    private final Map<UUID, PromoCode> editorContext = new ConcurrentHashMap<UUID, PromoCode>();

    /** Мастер-данные создания промокода */
    public static class Wizard {
        public String code;
        public RewardType type;
        public String command;
        public String itemBase64;
    }

    @Override
    public void onEnable() {
        SpigotAdapter adapter = new SpigotAdapter(this);
        core = new MyCodesCore(adapter);
        core.enable();
        core.setRewardDispatcher(rewardService = new RewardService(this));

        localBackend = new LocalBackend(this);
        bridge = new BridgeClient(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, dev.kilo.mycodes.common.BridgeProtocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, dev.kilo.mycodes.common.BridgeProtocol.CHANNEL, bridge);

        chatInput = new ChatInput(this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);

        commandService = new CommandService(core, new SpigotHooks());
        commandHandler = new SpigotCommandHandler(this, commandService);
        for (String name : new String[]{"mycodes", "code", "promo"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commandHandler);
                command.setTabCompleter(commandHandler);
            }
        }

        // если сервер задеплоен с игроками онлайн (например, после /reload) — стучимся к прокси сразу
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    bridge.requestHello();
                }
            }, 40L);
        }

        getLogger().info("MyCodes enabled (standalone mode). " +
                "If you run a BungeeCord/Waterfall/Velocity network, also install MyCodes on the proxy " +
                "— this server will automatically switch to network mode.");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.shutdown();
        }
    }

    // ── События ──

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // стук к прокси: если там есть MyCodes — включится сетевой режим
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    bridge.requestHello();
                }
            }
        }, 40L);
        // выдача наград, активированных оффлайн (только локальный режим; в сети — прокси сам пришлёт)
        if (remoteBackend == null) {
            Bukkit.getScheduler().runTaskAsynchronously(MyCodesSpigotPlugin.this, new Runnable() {
                @Override
                public void run() {
                    int delivered = core.promos().deliverOutstanding(
                            player.getUniqueId(), player.getName(), rewardService);
                    if (delivered > 0) {
                        getLogger().info("Delivered " + delivered + " pending reward(s) to " + player.getName());
                    }
                }
            });
        }
    }

    // ── Мост ──

    /** Первый контакт с прокси: включаем сетевой режим */
    public void onProxyConnected() {
        if (remoteBackend == null) {
            remoteBackend = new RemoteBackend(this);
            core.disableLocalTelegram(); // во избежание конфликта long-polling с ботом прокси
            bridge.requestData();
            getLogger().info("MyCodes proxy detected — this server switched to NETWORK mode (master data on the proxy).");
        }
    }

    /** Прокси прислал свежие настройки — синхронизируем язык сети */
    public void postConfigSnapshot(dev.kilo.mycodes.common.bridge.ConfigSnapshot snapshot) {
        try {
            if (snapshot.language != null && !snapshot.language.equals(core.lang().code())) {
                core.lang().load(snapshot.language);
                getLogger().info("Network language applied: " + snapshot.language);
            }
        } catch (Exception e) {
            getLogger().warning("Cannot switch network language: " + e.getMessage());
        }
    }

    public void onPromosSynced() {
        // кеш промокодов обновлён envoyом — можно ничего не делать (меню рисуются по требованию)
    }

    // ── Фасад ──

    public MyCodesCore core() {
        return core;
    }

    public Backend backend() {
        return remoteBackend != null ? remoteBackend : localBackend;
    }

    public BridgeClient bridge() {
        return bridge;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public CommandService commandService() {
        return commandService;
    }

    public RewardService rewardService() {
        return rewardService;
    }

    public boolean isRemoteMode() {
        return remoteBackend != null;
    }

    public String formatDate(long epochMs) {
        String format = isRemoteMode() && bridge.snapshot() != null && bridge.snapshot().dateFormat != null
                ? bridge.snapshot().dateFormat : core.config().dateFormat();
        return new SimpleDateFormat(format).format(new Date(epochMs));
    }

    // ── Мастер-редактирование и мастер-создание ──

    public void beginEdit(Player player, PromoCode promo) {
        editorContext.put(player.getUniqueId(), promo);
    }

    /** Сохранить изменения промокода и переоткрыть редактор */
    public void saveEdit(final Player player, final PromoCode promo) {
        editorContext.put(player.getUniqueId(), promo);
        backend().update(promo, new Consumer<String>() {
            @Override
            public void accept(String message) {
                player.sendMessage(message);
                if (player.isOnline()) {
                    new EditorMenu(MyCodesSpigotPlugin.this, player, promo).open();
                }
            }
        });
    }

    /** Мастер создания промокода: шаг 1 — код */
    public void startWizard(final Player player) {
        player.sendMessage(core.lang().msg("create-prompt-code",
                "cancel", core.lang().plain("cancel-word")));
        chatInput.request(player, new Consumer<String>() {
            @Override
            public void accept(String text) {
                String code = text.trim().toUpperCase();
                if (!CODE_PATTERN.matcher(code).matches()) {
                    player.sendMessage(core.lang().msg("create-invalid-code"));
                    return;
                }
                if (backend().promos() != null) {
                    for (PromoCode promo : backend().promos()) {
                        if (promo.code.equalsIgnoreCase(code)) {
                            player.sendMessage(core.lang().msg("create-exists", "code", code));
                            return;
                        }
                    }
                }
                Wizard wizard = new Wizard();
                wizard.code = code;
                wizards.put(player.getUniqueId(), wizard);
                new RewardTypeMenu(MyCodesSpigotPlugin.this, player).open();
            }
        });
    }

    /** Мастер: после выбора типа награды переходим к выбору сервера */
    public void continueWizard(final Player player, Wizard wizard) {
        new ServersMenu(this, player, new ServersMenu.SelectHandler() {
            @Override
            public void onSelect(final String server) {
                finishWizard(player, server);
            }
        }).open();
    }

    private void finishWizard(final Player player, final String server) {
        final Wizard wizard = wizards.remove(player.getUniqueId());
        if (wizard == null) {
            return;
        }
        PromoCode promo = new PromoCode();
        promo.code = wizard.code;
        promo.type = wizard.type == null || wizard.type == RewardType.COMMAND
                ? RewardType.COMMAND.name() : RewardType.ITEM.name();
        promo.command = wizard.command == null ? "" : wizard.command;
        promo.itemBase64 = wizard.itemBase64 == null ? "" : wizard.itemBase64;
        promo.server = server == null ? "any" : server;
        promo.effect = backend().defaultEffect().name();
        promo.effectDuration = backend().defaultEffectDuration();
        promo.rewardDelay = backend().defaultRewardDelay();
        promo.maxUses = 100;
        promo.oncePerPlayer = true;
        promo.telegramOnce = true;
        promo.telegramMode = "DEFAULT";
        promo.createdAt = System.currentTimeMillis();
        promo.enabled = true;

        Backend backend = backend();
        backend.create(promo, new Consumer<String>() {
            @Override
            public void accept(String message) {
                player.sendMessage(message);
                if (player.isOnline()) {
                    new ListMenu(MyCodesSpigotPlugin.this, player, 1).open();
                }
            }
        });
    }

    public Wizard wizard(UUID playerId) {
        return wizards.get(playerId);
    }

    public void wizardDone(UUID playerId) {
        wizards.remove(playerId);
    }

    public void playEffectPreview(Player player, EffectType type, int duration) {
        Effects.play(this, player, type, Math.max(1, duration), backend().effectSounds());
    }

    // ── Хуки общего сервиса команд ──

    private class SpigotHooks implements CommandService.Hooks {

        @Override
        public void openMenu(PlatformSender sender) {
            if (sender instanceof SpigotSender
                    && ((SpigotSender) sender).handle() instanceof Player) {
                Player player = (Player) ((SpigotSender) sender).handle();
                new dev.kilo.mycodes.spigot.gui.menu.MainMenu(MyCodesSpigotPlugin.this, player).open();
            } else {
                sender.sendLegacy(core.lang().msg("player-only"));
            }
        }

        @Override
        public RedeemResult redeem(final PlatformSender sender, String code) {
            if (!(sender instanceof SpigotSender)
                    || !(((SpigotSender) sender).handle() instanceof Player)) {
                sender.sendLegacy(core.lang().msg("player-only"));
                return null;
            }
            final Player player = (Player) ((SpigotSender) sender).handle();
            backend().redeem(player, code, new Consumer<RedeemResult>() {
                @Override
                public void accept(RedeemResult result) {
                    commandService.sendResult(sender, result);
                    if (result.isSuccess() && result.reward != null) {
                        rewardService.give(player, player.getName(), result.reward);
                    }
                }
            });
            return null; // ответ асинхронный
        }

        @Override
        public String currentServer(PlatformSender sender) {
            return null; // одиночный режим — ограничений сервера нет
        }

        @Override
        public void reload(final PlatformSender sender) {
            backend().reload(new Consumer<String>() {
                @Override
                public void accept(String message) {
                    sender.sendLegacy(message);
                }
            });
            if (!isRemoteMode()) {
                sender.sendLegacy(core.lang().msg("reloaded"));
            }
        }

        @Override
        public boolean isRemoteMode() {
            return isRemoteMode0();
        }

        @Override
        public List<PromoCode> promos() {
            return backend().promos();
        }

        private boolean isRemoteMode0() {
            return remoteBackend != null;
        }
    }
}
