package dev.kilo.mycodes.spigot.bridge;

import com.google.gson.JsonObject;
import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.bridge.ConfigSnapshot;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.util.JsonUtil;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Клиент моста на стороне backend: приём данных промокодов и настроек с прокси,
 * отправка действий (создание/редактирование/активация) на прокси.
 *
 * <p>Пока прокси не прислал CONFIG, плагин работает в одиночном режиме.
 */
public class BridgeClient implements PluginMessageListener {

    private final MyCodesSpigotPlugin plugin;
    private final Map<String, Consumer<JsonObject>> callbacks = new ConcurrentHashMap<String, Consumer<JsonObject>>();
    private final Random random = new Random();

    private volatile ConfigSnapshot snapshot;
    private volatile List<PromoCode> promos = new ArrayList<PromoCode>();
    private volatile boolean remote;

    public BridgeClient(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRemote() {
        return remote;
    }

    public ConfigSnapshot snapshot() {
        return snapshot;
    }

    public List<PromoCode> promos() {
        return promos;
    }

    /** Отправить пакет на прокси; false если нет ни одного игрока-«носителя» */
    public boolean send(String type, String json) {
        Player carrier = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            carrier = p;
            break;
        }
        if (carrier == null) return false;
        byte[] data = BridgeProtocol.encode(type, json);
        trySend(carrier, data);
        return true;
    }

    private void trySend(Player carrier, byte[] data) {
        try {
            carrier.sendPluginMessage(plugin, BridgeProtocol.CHANNEL, data);
        } catch (IllegalStateException e) {
            // канал ещё не пробужден у этого игрока — повтор через секунду
            final Player p = carrier;
            final byte[] bytes = data;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        try {
                            p.sendPluginMessage(plugin, BridgeProtocol.CHANNEL, bytes);
                        } catch (IllegalStateException ignored) {
                        }
                    }
                }
            }, 20L);
        }
    }

    /** Отправить с rid и ждать ответа done/redeemresult для этого rid */
    public void sendWithCallback(String type, String json, Consumer<JsonObject> callback) {
        String rid = newRequestId();
        JsonObject payload = JsonUtil.parseObject(json == null ? "{}" : json);
        payload.addProperty("rid", rid);
        registerCallback(rid, callback);
        boolean ok = send(type, payload.toString());
        if (!ok) {
            callbacks.remove(rid);
            JsonObject err = new JsonObject();
            err.addProperty("ok", false);
            err.addProperty("message", plugin.core().lang().msg("bridge-unavailable"));
            callback.accept(err);
        }
    }

    private void registerCallback(final String rid, Consumer<JsonObject> callback) {
        callbacks.put(rid, callback);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                callbacks.remove(rid);
            }
        }, 20L * 40);
    }

    public boolean hasCarrier() {
        return !Bukkit.getOnlinePlayers().isEmpty();
    }

    public void requestHello() {
        JsonObject payload = new JsonObject();
        payload.addProperty("server", "spigot");
        payload.addProperty("version", "1.0.0");
        send(BridgeProtocol.HELLO, payload.toString());
    }

    public void requestData() {
        JsonObject payload = new JsonObject();
        send(BridgeProtocol.GET_DATA, payload.toString());
    }

    public void redeem(Player player, String code, Consumer<RedeemResult> callback) {
        JsonObject payload = new JsonObject();
        String rid = newRequestId();
        payload.addProperty("rid", rid);
        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("name", player.getName());
        payload.addProperty("code", code);
        registerCallback(rid, new Consumer<JsonObject>() {
            @Override
            public void accept(JsonObject json) {
                callback.accept(parseRedeemResult(json));
            }
        });
        boolean sent = send(BridgeProtocol.REDEEM, payload.toString());
        if (!sent) {
            callbacks.remove(rid);
            callback.accept(RedeemResult.error(plugin.core().lang().msg("bridge-unavailable")));
        }
    }

    private RedeemResult parseRedeemResult(JsonObject json) {
        String message = json.has("message") ? json.get("message").getAsString() : "";
        String linkUrl = json.has("linkUrl") && !json.get("linkUrl").isJsonNull()
                ? json.get("linkUrl").getAsString() : null;
        String linkText = json.has("linkText") && !json.get("linkText").isJsonNull()
                ? json.get("linkText").getAsString() : null;
        String promoCode = json.has("promoCode") && !json.get("promoCode").isJsonNull()
                ? json.get("promoCode").getAsString() : null;
        if (linkUrl != null && !linkUrl.isEmpty()) {
            return RedeemResult.pending(message, promoCode, linkUrl,
                    linkText == null ? linkUrl : linkText);
        }
        boolean ok = json.has("success") && json.get("success").getAsBoolean();
        if (ok) {
            RewardData reward = null;
            if (json.has("reward") && json.get("reward").isJsonObject()) {
                reward = JsonUtil.fromJson(json.get("reward").toString(), RewardData.class);
            }
            return RedeemResult.success(message, promoCode, reward);
        }
        return RedeemResult.error(message);
    }

    private String newRequestId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" + Integer.toHexString(random.nextInt());
    }

    // ── Приём пакетов от прокси ──

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] bytes) {
        if (!BridgeProtocol.CHANNEL.equals(channel)) return;
        String[] frame = BridgeProtocol.decode(bytes);
        if (frame == null) return;
        final String type = frame[0];
        final String json = frame.length > 1 && frame[1] != null && !frame[1].isEmpty() ? frame[1] : "{}";
        Runnable handler = new Runnable() {
            @Override
            public void run() {
                try {
                    handle(type, json);
                } catch (Exception e) {
                    plugin.getLogger().warning("Bridge packet error (" + type + "): " + e);
                }
            }
        };
        if (Bukkit.isPrimaryThread()) {
            handler.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, handler);
        }
    }

    private void handle(String type, String json) {
        JsonObject obj = JsonUtil.parseObject(json);
        if (BridgeProtocol.CONFIG.equals(type)) {
            ConfigSnapshot incoming = JsonUtil.fromJson(json, ConfigSnapshot.class);
            if (incoming == null) return;
            boolean firstContact = !remote;
            snapshot = incoming;
            remote = true;
            if (firstContact) {
                plugin.onProxyConnected();
            }
            plugin.postConfigSnapshot(incoming);
        } else if (BridgeProtocol.PROMOS.equals(type)) {
            List<PromoCode> list = new ArrayList<PromoCode>();
            try {
                if (obj.has("list") && obj.get("list").isJsonArray()) {
                    com.google.gson.JsonArray array = obj.getAsJsonArray("list");
                    for (int i = 0; i < array.size(); i++) {
                        PromoCode promo = JsonUtil.fromJson(array.get(i).toString(), PromoCode.class);
                        if (promo != null) list.add(promo);
                    }
                }
                promos = list;
            } catch (Exception e) {
                plugin.getLogger().warning("Bridge: bad promos payload: " + e.getMessage());
            }
            plugin.onPromosSynced();
        } else if (BridgeProtocol.REDEEM_RESULT.equals(type)) {
            String rid = obj.has("rid") ? obj.get("rid").getAsString() : null;
            Consumer<JsonObject> callback = rid != null ? callbacks.remove(rid) : null;
            if (callback != null) {
                callback.accept(obj);
            }
        } else if (BridgeProtocol.REWARD.equals(type)) {
            String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
            String name = obj.has("name") ? obj.get("name").getAsString() : "?";
            RewardData reward = obj.has("reward") && obj.get("reward").isJsonObject()
                    ? JsonUtil.fromJson(obj.get("reward").toString(), RewardData.class) : null;
            if (uuid == null || reward == null) return;
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(uuid));
                if (target != null && target.isOnline()) {
                    plugin.rewardService().give(target, name, reward);
                }
            } catch (IllegalArgumentException ignored) {
            }
        } else if (BridgeProtocol.DONE.equals(type)) {
            String rid = obj.has("rid") ? obj.get("rid").getAsString() : null;
            Consumer<JsonObject> callback = rid != null ? callbacks.remove(rid) : null;
            if (callback != null) {
                callback.accept(obj);
            }
        } else if (BridgeProtocol.BROADCAST.equals(type)) {
            String message = obj.has("message") ? obj.get("message").getAsString() : "";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(message.split("\n"));
            }
        }
    }
}
