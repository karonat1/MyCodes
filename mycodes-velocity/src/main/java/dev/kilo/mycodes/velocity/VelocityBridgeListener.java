package dev.kilo.mycodes.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.promo.PromoManager;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.common.settings.SettingsPatch;
import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import dev.kilo.mycodes.common.util.JsonUtil;

import java.util.Arrays;
import java.util.UUID;

/**
 * Серверная сторона моста на Velocity.
 */
public class VelocityBridgeListener {

    private final MyCodesVelocityPlugin plugin;

    public VelocityBridgeListener(MyCodesVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!MyCodesVelocityPlugin.CHANNEL.equals(event.getIdentifier())) return;
        if (!(event.getSource() instanceof ServerConnection)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        ServerConnection server = (ServerConnection) event.getSource();
        plugin.dispatcher().markConnected(server.getPlayer().getUniqueId());

        String[] frame = BridgeProtocol.decode(event.getData());
        if (frame == null) return;
        String type = frame[0];
        String json = frame.length > 1 && frame[1] != null && !frame[1].isEmpty() ? frame[1] : "{}";
        try {
            handle(type, json, server);
        } catch (Exception e) {
            plugin.core().adapter().logWarn("bridge packet error (" + type + "): " + e);
        }
    }

    private void handle(String type, String json, ServerConnection server) {
        String serverName = server.getServerInfo().getName();
        plugin.dispatcher().markBridged(serverName);
        com.google.gson.JsonObject obj = JsonUtil.parseObject(json);

        if (BridgeProtocol.HELLO.equals(type) || BridgeProtocol.GET_DATA.equals(type)) {
            server.sendPluginMessage(MyCodesVelocityPlugin.CHANNEL,
                    BridgeProtocol.encode(BridgeProtocol.CONFIG, plugin.configJson()));
            server.sendPluginMessage(MyCodesVelocityPlugin.CHANNEL,
                    BridgeProtocol.encode(BridgeProtocol.PROMOS, plugin.promosJson()));
        } else if (BridgeProtocol.CREATE_PROMO.equals(type)) {
            PromoCode promo = JsonUtil.fromJson(json, PromoCode.class);
            if (promo == null) return;
            PromoManager.CreateResult result = plugin.core().promos().create(promo);
            plugin.pushPromosToAll();
            if (obj.has("rid")) {
                sendDone(server, obj.get("rid").getAsString(), result.ok, result.message);
            }
        } else if (BridgeProtocol.UPDATE_PROMO.equals(type)) {
            PromoCode promo = JsonUtil.fromJson(json, PromoCode.class);
            if (promo == null) return;
            boolean ok = plugin.core().promos().update(promo);
            plugin.pushPromosToAll();
            if (obj.has("rid")) {
                sendDone(server, obj.get("rid").getAsString(), ok,
                        ok ? plugin.core().lang().msg("editor-updated")
                           : plugin.core().lang().msg("storage-error"));
            }
        } else if (BridgeProtocol.DELETE_PROMO.equals(type)) {
            String id = obj.has("id") ? obj.get("id").getAsString() : null;
            String code = obj.has("code") ? obj.get("code").getAsString() : "";
            boolean ok = id != null && plugin.core().promos().delete(id);
            plugin.pushPromosToAll();
            if (obj.has("rid")) {
                sendDone(server, obj.get("rid").getAsString(), ok,
                        ok ? plugin.core().lang().msg("promo-deleted", "code", code)
                           : plugin.core().lang().msg("storage-error"));
            }
        } else if (BridgeProtocol.REDEEM.equals(type)) {
            handleRedeem(obj, server);
        } else if (BridgeProtocol.RELOAD.equals(type)) {
            plugin.core().reload();
            plugin.pushAll();
            if (obj.has("rid")) {
                sendDone(server, obj.get("rid").getAsString(), true, plugin.core().lang().msg("reloaded"));
            }
        } else if (BridgeProtocol.SETTINGS.equals(type)) {
            handleSettings(obj);
            plugin.core().reload();
            plugin.pushAll();
            if (obj.has("rid")) {
                sendDone(server, obj.get("rid").getAsString(), true, plugin.core().lang().msg("settings-done"));
            }
        }
    }

    private void handleRedeem(com.google.gson.JsonObject obj, ServerConnection server) {
        String rid = obj.has("rid") ? obj.get("rid").getAsString() : null;
        String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
        String name = obj.has("name") ? obj.get("name").getAsString() : "?";
        String code = obj.has("code") ? obj.get("code").getAsString() : "";
        RedeemResult result = plugin.core().promos().redeem(
                UUID.fromString(uuid), name, server.getServerInfo().getName(), code);
        com.google.gson.JsonObject reply = new com.google.gson.JsonObject();
        if (rid != null) reply.addProperty("rid", rid);
        reply.addProperty("success", result.status == RedeemResult.Status.SUCCESS);
        reply.addProperty("message", result.message);
        if (result.promoCode != null) reply.addProperty("promoCode", result.promoCode);
        if (result.status == RedeemResult.Status.PENDING_TELEGRAM) {
            reply.addProperty("linkUrl", result.linkUrl);
            reply.addProperty("linkText", result.linkText);
        }
        if (result.status == RedeemResult.Status.SUCCESS && result.reward != null) {
            RewardData reward = result.reward;
            reply.add("reward", JsonUtil.parseElement(JsonUtil.toJson(reward)));
            boolean delivered = plugin.dispatcher().dispatch(UUID.fromString(uuid), name, null, reward);
            if (delivered && reward.activationId > 0) {
                plugin.core().promos().markRewardedById(reward.activationId);
            }
        }
        server.sendPluginMessage(MyCodesVelocityPlugin.CHANNEL,
                BridgeProtocol.encode(BridgeProtocol.REDEEM_RESULT, reply.toString()));
    }

    private void handleSettings(com.google.gson.JsonObject obj) {
        boolean telegramKind = obj.has("enabled") || obj.has("token")
                || obj.has("chatId") || obj.has("require")
                || obj.has("antiMulti") || obj.has("notify");
        try {
            if (telegramKind) {
                com.google.gson.JsonObject cleaned = new com.google.gson.JsonObject();
                for (String key : Arrays.asList("enabled", "token", "chatId", "require", "antiMulti", "notify")) {
                    if (obj.has(key)) cleaned.add(key, obj.get(key));
                }
                TelegramSettingsUpdate update = JsonUtil.fromJson(cleaned.toString(), TelegramSettingsUpdate.class);
                if (update != null) {
                    SettingsPatch.applyTelegram(plugin.core().adapter().dataFolder().resolve("config.yml"), update);
                }
            } else {
                com.google.gson.JsonObject cleaned = new com.google.gson.JsonObject();
                for (String key : Arrays.asList("language", "defaultEffect", "defaultDuration",
                        "defaultDelay", "sounds", "broadcastOnCreate")) {
                    if (obj.has(key)) cleaned.add(key, obj.get(key));
                }
                GeneralSettingsUpdate update = JsonUtil.fromJson(cleaned.toString(), GeneralSettingsUpdate.class);
                if (update != null) {
                    SettingsPatch.applyGeneral(plugin.core().adapter().dataFolder().resolve("config.yml"), update);
                }
            }
        } catch (Exception e) {
            plugin.core().adapter().logWarn("failed to apply settings from menu: " + e.getMessage());
        }
    }

    private void sendDone(ServerConnection server, String rid, boolean ok, String message) {
        com.google.gson.JsonObject done = new com.google.gson.JsonObject();
        done.addProperty("rid", rid);
        done.addProperty("ok", ok);
        done.addProperty("message", message);
        server.sendPluginMessage(MyCodesVelocityPlugin.CHANNEL,
                BridgeProtocol.encode(BridgeProtocol.DONE, done.toString()));
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        final com.velocitypowered.api.proxy.Player player = event.getPlayer();
        plugin.core().adapter().async().execute(new Runnable() {
            @Override
            public void run() {
                int delivered = plugin.core().promos().deliverOutstanding(
                        player.getUniqueId(), player.getUsername(), plugin.dispatcher());
                if (delivered > 0) {
                    plugin.core().adapter().logInfo("delivered " + delivered
                            + " pending reward(s) to " + player.getUsername());
                }
            }
        });
    }
}
