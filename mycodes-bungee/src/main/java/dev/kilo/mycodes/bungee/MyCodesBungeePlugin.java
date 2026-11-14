package dev.kilo.mycodes.bungee;

import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.MyCodesCore;
import dev.kilo.mycodes.common.PlatformAdapter;
import dev.kilo.mycodes.common.PlatformSender;
import dev.kilo.mycodes.common.bridge.ConfigSnapshot;
import dev.kilo.mycodes.common.command.CommandService;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.util.JsonUtil;
import dev.kilo.mycodes.common.util.TextUtil;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.List;

/**
 * MyCodes для BungeeCord/Waterfall: мастер-данные сети, мост к backend'ам,
 * команды, Telegram-интеграция. Меню открывается на backend-серверах
 * (установите туда MyCodes-Spigot).
 */
public class MyCodesBungeePlugin extends Plugin {

    private MyCodesCore core;
    private BungeeRewardDispatcher dispatcher;
    private CommandService commandService;

    private BungeeAdapter adapter;

    @Override
    public void onEnable() {
        adapter = new BungeeAdapter(this);
        core = new MyCodesCore(adapter);
        core.enable();
        dispatcher = new BungeeRewardDispatcher(this);
        core.setRewardDispatcher(dispatcher);

        ProxyServer proxy = getProxy();
        proxy.registerChannel(BridgeProtocol.CHANNEL);
        proxy.getPluginManager().registerListener(this, new BridgeServerListener(this));

        commandService = new CommandService(core, new BungeeHooks());
        proxy.getPluginManager().registerCommand(this, new dev.kilo.mycodes.bungee.command.MycodesCommand(commandService));
        proxy.getPluginManager().registerCommand(this, new dev.kilo.mycodes.bungee.command.CodeCommand(commandService));
        proxy.getPluginManager().registerCommand(this, new dev.kilo.mycodes.bungee.command.PromoCommand(commandService));

        getLogger().info("MyCodes enabled on BungeeCord. Install MyCodes-Spigot on your backend servers "
                + "to get full GUI menus, promo effects and reward execution there.");
    }

    @Override
    public void onDisable() {
        try {
            getProxy().unregisterChannel(BridgeProtocol.CHANNEL);
        } catch (Exception ignored) {
        }
        if (core != null) {
            core.shutdown();
        }
    }

    public MyCodesCore core() {
        return core;
    }

    public BungeeRewardDispatcher dispatcher() {
        return dispatcher;
    }

    public CommandService commandService() {
        return commandService;
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

    public void pushConfigTo(Server server) {
        server.sendData(BridgeProtocol.CHANNEL,
                BridgeProtocol.encode(BridgeProtocol.CONFIG, configJson()));
    }

    public void pushPromosTo(Server server) {
        server.sendData(BridgeProtocol.CHANNEL,
                BridgeProtocol.encode(BridgeProtocol.PROMOS, promosJson()));
    }

    public void pushPromosToAll() {
        dispatcher.broadcastToServers(BridgeProtocol.PROMOS, promosJson());
    }

    public void pushAll() {
        dispatcher.broadcastToServers(BridgeProtocol.CONFIG, configJson());
        dispatcher.broadcastToServers(BridgeProtocol.PROMOS, promosJson());
    }

    // ── Хуки общего сервиса команд ──

    private class BungeeHooks implements CommandService.Hooks {

        @Override
        public void openMenu(PlatformSender sender) {
            sender.sendLegacy(core.lang().msg("proxy-menu-hint"));
        }

        @Override
        public RedeemResult redeem(PlatformSender sender, String code) {
            if (!sender.isPlayer()) {
                sender.sendLegacy(core.lang().msg("player-only"));
                return null;
            }
            String serverName = currentServer(sender);
            RedeemResult result = core.promos().redeem(sender.uuid(), sender.name(), serverName, code);
            payReward(sender.uuid(), sender.name(), result);
            return result;
        }

        @Override
        public String currentServer(PlatformSender sender) {
            if (!sender.isPlayer()) return null;
            net.md_5.bungee.api.connection.ProxiedPlayer player =
                    getProxy().getPlayer(sender.uuid());
            Server server = player.getServer();
            return server != null ? server.getInfo().getName() : null;
        }

        @Override
        public void reload(PlatformSender sender) {
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
            if (result.status == RedeemResult.Status.SUCCESS && result.reward != null) {
                boolean delivered = dispatcher.dispatch(uuid, name, null, result.reward);
                if (delivered && result.reward.activationId > 0) {
                    core.promos().markRewardedById(result.reward.activationId);
                }
            }
        }
    }
}
