package dev.kilo.mycodes.bungee;

import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.promo.PromoManager;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.common.settings.SettingsPatch;
import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import dev.kilo.mycodes.common.util.JsonUtil;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Arrays;
import java.util.UUID;

/**
 * Серверная сторона моста на BungeeCord/Waterfall: принимает пакеты от
 * backend-плагинов MyCodes-Spigot и управляет мастер-данными.
 */
public class BridgeServerListener implements Listener {

    private final MyCodesBungeePlugin plugin;

    public BridgeServerListener(MyCodesBungeePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Мост ──

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!BridgeProtocol.CHANNEL.equals(event.getTag())) return;
        if (!(event.getSender() instanceof Server)) return;
        event.setCancelled(true);
        Server server = (Server) event.getSender();
        if (!(event.getReceiver() instanceof ProxiedPlayer)) return;
        ProxiedPlayer carrier = (ProxiedPlayer) event.getReceiver();

        String[] frame = BridgeProtocol.decode(event.getData());
        if (frame == null) return;
        String type = frame[0];
        String json = frame.length > 1 && frame[1] != null && !frame[1].isEmpty() ? frame[1] : "{}";
        try {
            handle(type, json, server, carrier);
        } catch (Exception e) {
            plugin.getLogger().warning("Bridge packet error (" + type + "): " + e);
        }
    }

    private void handle(String type, String json, Server server, ProxiedPlayer carrier) {
        plugin.dispatcher().markBridged(server.getInfo().getName());
        com.google.gson.JsonObject obj = JsonUtil.parseObject(json);

        if (BridgeProtocol.HELLO.equals(type) || BridgeProtocol.GET_DATA.equals(type)) {
            plugin.pushConfigTo(server);
            plugin.pushPromosTo(server);
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
            String rid = obj.has("rid") ? obj.get("rid").getAsString() : null;
            plugin.core().reload();
            plugin.pushAll();
            if (rid != null) {
                sendDone(server, rid, true, plugin.core().lang().msg("reloaded"));
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

    private void handleRedeem(com.google.gson.JsonObject obj, Server server) {
        String rid = obj.has("rid") ? obj.get("rid").getAsString() : null;
        String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
        String name = obj.has("name") ? obj.get("name").getAsString() : "?";
        String code = obj.has("code") ? obj.get("code").getAsString() : "";
        RedeemResult result = plugin.core().promos().redeem(
                UUID.fromString(uuid), name, server.getInfo().getName(), code);
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
            reply.add("reward", JsonUtil.parseObject(JsonUtil.toJson(reward)));
            // выдача с эффектом — на сервере игрока (или консоль прокси без моста)
            boolean delivered = plugin.dispatcher().dispatch(UUID.fromString(uuid), name, null, reward);
            if (delivered && reward.activationId > 0) {
                plugin.core().promos().markRewardedById(reward.activationId);
            }
        }
        server.sendData(BridgeProtocol.CHANNEL,
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
            plugin.getLogger().warning("Failed to apply settings from menu: " + e.getMessage());
        }
    }

    private void sendDone(Server server, String rid, boolean ok, String message) {
        com.google.gson.JsonObject done = new com.google.gson.JsonObject();
        done.addProperty("rid", rid);
        done.addProperty("ok", ok);
        done.addProperty("message", message);
        server.sendData(BridgeProtocol.CHANNEL,
                BridgeProtocol.encode(BridgeProtocol.DONE, done.toString()));
    }

    // ── Вход игрока / смена сервера: выдача накопленных наград ──

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        final ProxiedPlayer player = event.getPlayer();
        plugin.core().adapter().async().execute(new Runnable() {
            @Override
            public void run() {
                int delivered = plugin.core().promos().deliverOutstanding(
                        player.getUniqueId(), player.getName(), plugin.dispatcher());
                if (delivered > 0) {
                    plugin.getLogger().info("Delivered " + delivered + " pending reward(s) to " + player.getName());
                }
            }
        });
    }

    // ── Tab-complete подсказки для /mcd ──

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String cursor = event.getCursor().toLowerCase();
        if (!(cursor.startsWith("/mcd ") || cursor.startsWith("/mycodes ")
                || cursor.equals("/mcd") || cursor.equals("/mycodes"))) {
            return;
        }
        String[] parts = cursor.substring(1).split(" ");
        if (parts.length <= 1) {
            event.getSuggestions().addAll(Arrays.asList(
                    "help", "menu", "list", "reload", "code"));
            return;
        }
        String partial = parts.length == 2 ? parts[1] : "";
        for (String sub : Arrays.asList("help", "menu", "list", "reload", "code")) {
            if (sub.startsWith(partial)) {
                event.getSuggestions().add(sub);
            }
        }
    }
}
