package dev.kilo.mycodes.spigot.backend;

import com.google.gson.JsonObject;
import dev.kilo.mycodes.common.BridgeProtocol;
import dev.kilo.mycodes.common.bridge.ConfigSnapshot;
import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import dev.kilo.mycodes.common.util.JsonUtil;
import dev.kilo.mycodes.spigot.MyCodesSpigotPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Сетевой режим: мастер-данные живут на прокси (BungeeCord/Waterfall/Velocity),
 * а этот сервер отображает меню, исполняет команды награды и эффекты.
 */
public class RemoteBackend implements Backend {

    private final MyCodesSpigotPlugin plugin;

    public RemoteBackend(MyCodesSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigSnapshot snapshot() {
        return plugin.bridge().snapshot();
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public String platformLabel() {
        ConfigSnapshot s = snapshot();
        return s == null ? "network" : s.proxyType;
    }

    @Override
    public List<PromoCode> promos() {
        return plugin.bridge().promos();
    }

    @Override
    public List<String> servers() {
        ConfigSnapshot s = snapshot();
        return s == null ? new ArrayList<String>() : s.servers;
    }

    @Override
    public String language() {
        ConfigSnapshot s = snapshot();
        return s == null ? plugin.core().config().language() : s.language;
    }

    @Override
    public EffectType defaultEffect() {
        ConfigSnapshot s = snapshot();
        return s == null ? EffectType.FIREWORK : EffectType.of(s.defaultEffect);
    }

    @Override
    public int defaultEffectDuration() {
        ConfigSnapshot s = snapshot();
        return s == null ? 3 : s.defaultDuration;
    }

    @Override
    public int defaultRewardDelay() {
        ConfigSnapshot s = snapshot();
        return s == null ? 0 : s.defaultRewardDelay;
    }

    @Override
    public boolean effectSounds() {
        ConfigSnapshot s = snapshot();
        return s == null || s.sounds;
    }

    @Override
    public boolean broadcastOnCreate() {
        ConfigSnapshot s = snapshot();
        return s == null || s.broadcastOnCreate;
    }

    @Override
    public boolean telegramEnabled() {
        ConfigSnapshot s = snapshot();
        return s != null && s.telegramEnabled;
    }

    @Override
    public boolean telegramConnected() {
        ConfigSnapshot s = snapshot();
        return s != null && s.telegramConnected;
    }

    @Override
    public String telegramUsername() {
        ConfigSnapshot s = snapshot();
        return s == null ? "" : s.telegramUsername;
    }

    @Override
    public boolean telegramRequire() {
        ConfigSnapshot s = snapshot();
        return s != null && s.telegramRequire;
    }

    @Override
    public boolean telegramAntiMulti() {
        ConfigSnapshot s = snapshot();
        return s == null || s.telegramAntiMulti;
    }

    @Override
    public boolean telegramNotify() {
        ConfigSnapshot s = snapshot();
        return s == null || s.telegramNotify;
    }

    @Override
    public String telegramChatId() {
        ConfigSnapshot s = snapshot();
        return s == null ? "" : s.telegramChatId;
    }

    @Override
    public void refreshAsync() {
        plugin.bridge().requestData();
    }

    @Override
    public void create(PromoCode promo, final Consumer<String> feedback) {
        sendAction(BridgeProtocol.CREATE_PROMO, JsonUtil.toJson(promo), feedback);
    }

    @Override
    public void update(PromoCode promo, final Consumer<String> feedback) {
        sendAction(BridgeProtocol.UPDATE_PROMO, JsonUtil.toJson(promo), feedback);
    }

    @Override
    public void delete(String promoId, String code, final Consumer<String> feedback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("id", promoId);
        payload.addProperty("code", code == null ? "" : code);
        sendAction(BridgeProtocol.DELETE_PROMO, payload.toString(), feedback);
    }

    @Override
    public void updateGeneral(GeneralSettingsUpdate update, final Consumer<String> feedback) {
        sendAction(BridgeProtocol.SETTINGS, JsonUtil.toJson(update), feedback);
    }

    @Override
    public void updateTelegram(TelegramSettingsUpdate update, final Consumer<String> feedback) {
        sendAction(BridgeProtocol.SETTINGS, JsonUtil.toJson(update), feedback);
    }

    private void sendAction(final String type, String json, final Consumer<String> feedback) {
        plugin.bridge().sendWithCallback(type, json, new Consumer<JsonObject>() {
            @Override
            public void accept(JsonObject response) {
                String message = response.has("message") && !response.get("message").isJsonNull()
                        ? response.get("message").getAsString() : null;
                if (message == null || message.isEmpty()) {
                    message = plugin.core().lang().msg("bridge-unavailable");
                }
                feedback.accept(message);
            }
        });
    }

    @Override
    public void reload(final Consumer<String> feedback) {
        plugin.bridge().sendWithCallback(BridgeProtocol.RELOAD, "{}", new Consumer<JsonObject>() {
            @Override
            public void accept(JsonObject response) {
                String message = response.has("message") && !response.get("message").isJsonNull()
                        ? response.get("message").getAsString()
                        : plugin.core().lang().msg("reloaded");
                feedback.accept(message);
            }
        });
    }

    @Override
    public void redeem(Player player, String code, Consumer<RedeemResult> callback) {
        plugin.bridge().redeem(player, code, callback);
    }
}
