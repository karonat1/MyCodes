package dev.kilo.mycodes.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.MyCodesCore;
import dev.kilo.mycodes.common.bridge.ConfigSnapshot;
import dev.kilo.mycodes.common.command.CommandService;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.util.JsonUtil;
import dev.kilo.mycodes.velocity.command.VelocityCommands;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.nio.file.Path;
import java.util.List;

/**
 * MyCodes для Velocity: мастер-данные сети, мост к backend'ам,
 * команды и Telegram-интеграция. Меню открывается на backend-серверах
 * (установите туда MyCodes-Spigot).
 */
@Plugin(id = "mycodes",
        name = "MyCodes",
        version = "1.0.0",
        description = "Promo code system with GUI, effects and Telegram integration",
        authors = {"Kilo"})
public class MyCodesVelocityPlugin {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("mycodes", "bridge");

    private final ProxyServer proxy;
    private final Path dataDirectory;

    private MyCodesCore core;
    private VelocityRewardDispatcher dispatcher;
    private CommandService commandService;

    @Inject
    public MyCodesVelocityPlugin(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        core = new MyCodesCore(new VelocityAdapter(this));
        core.enable();
        dispatcher = new VelocityRewardDispatcher(this);
        core.setRewardDispatcher(dispatcher);

        ChannelRegistrar registrar = proxy.getChannelRegistrar();
        registrar.register(CHANNEL);
        proxy.getEventManager().register(this, new VelocityBridgeListener(this));

        commandService = new CommandService(core, new VelocityHooks());
        VelocityCommands.registerAll(this, proxy.getCommandManager(), commandService);

        proxy.getConsoleCommandSource().sendMessage(LegacyComponentSerializer.legacySection()
                .deserialize("§b[MyCodes] §7enabled on Velocity. Install MyCodes-Spigot on backend servers "
                        + "for GUI menus and promo effects."));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        try {
            proxy.getChannelRegistrar().unregister(CHANNEL);
        } catch (Exception ignored) {
        }
        if (core != null) {
            core.shutdown();
        }
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public MyCodesCore core() {
        return core;
    }

    public VelocityRewardDispatcher dispatcher() {
        return dispatcher;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    // ── Рассылка данных по мосту ──

    public String configJson() {
        return JsonUtil.toJson(new ConfigSnapshot().copyFromConfig(core));
    }

    public String promosJson() {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (PromoCode promo : core.promos().all()) {
            array.add(JsonUtil.parseElement(JsonUtil.toJson(promo)));
        }
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.add("list", array);
        return payload.toString();
    }

    public void pushPromosToAll() {
        String json = promosJson();
        for (com.velocitypowered.api.proxy.server.RegisteredServer server : proxy.getAllServers()) {
            for (com.velocitypowered.api.proxy.Player player : server.getPlayersConnected()) {
                if (dispatcher.isConnected(player)) {
                    player.getCurrentServer().ifPresent(conn ->
                            conn.sendPluginMessage(CHANNEL,
                                    BridgeProtocol.encode(BridgeProtocol.PROMOS, json)));
                    break;
                }
            }
        }
    }

    public void pushAll() {
        String configJson = configJson();
        for (com.velocitypowered.api.proxy.server.RegisteredServer server : proxy.getAllServers()) {
            for (com.velocitypowered.api.proxy.Player player : server.getPlayersConnected()) {
                if (dispatcher.isConnected(player)) {
                    player.getCurrentServer().ifPresent(conn -> {
                        conn.sendPluginMessage(CHANNEL,
                                BridgeProtocol.encode(BridgeProtocol.CONFIG, configJson));
                        conn.sendPluginMessage(CHANNEL,
                                BridgeProtocol.encode(BridgeProtocol.PROMOS, promosJson()));
                    });
                }
            }
        }
    }

    // ── Хуки общего сервиса команд ──

    private class VelocityHooks implements CommandService.Hooks {

        @Override
        public void openMenu(dev.kilo.mycodes.common.PlatformSender sender) {
            sender.sendLegacy(core.lang().msg("proxy-menu-hint"));
        }

        @Override
        public dev.kilo.mycodes.common.model.RedeemResult redeem(
                dev.kilo.mycodes.common.PlatformSender sender, String code) {
            if (!sender.isPlayer()) {
                sender.sendLegacy(core.lang().msg("player-only"));
                return null;
            }
            RedeemResult result = core.promos().redeem(
                    sender.uuid(), sender.name(), currentServer(sender), code);
            payReward(sender.uuid(), sender.name(), result);
            return result;
        }

        @Override
        public String currentServer(dev.kilo.mycodes.common.PlatformSender sender) {
            if (!sender.isPlayer()) return null;
            return proxy.getPlayer(sender.uuid())
                    .flatMap(com.velocitypowered.api.proxy.Player::getCurrentServer)
                    .map(conn -> conn.getServerInfo().getName())
                    .orElse(null);
        }

        @Override
        public void reload(dev.kilo.mycodes.common.PlatformSender sender) {
            core.reload();
            pushAll();
            sender.sendLegacy(core.lang().msg("reloaded"));
        }

        @Override
        public boolean isRemoteMode() {
            return false;
        }

        @Override
        public List<PromoCode> promos() {
            return core.promos().all();
        }

        private void payReward(java.util.UUID uuid, String name, RedeemResult result) {
            if (result != null
                    && result.status == RedeemResult.Status.SUCCESS && result.reward != null) {
                boolean delivered = dispatcher.dispatch(uuid, name, null, result.reward);
                if (delivered && result.reward.activationId > 0) {
                    core.promos().markRewardedById(result.reward.activationId);
                }
            }
        }
    }
}
