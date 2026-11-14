package dev.kilo.mycodes.common.promo;

import dev.kilo.mycodes.common.MyCodesCore;
import dev.kilo.mycodes.common.model.Activation;
import dev.kilo.mycodes.common.model.PendingLink;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.model.RewardData;
import dev.kilo.mycodes.common.model.TgBinding;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Бизнес-логика промокодов: создание, редемпция, подтверждение через Telegram,
 * лимиты, анти-мультиаккаунт. Потокобезопасно.
 */
public class PromoManager {

    public static class CreateResult {
        public boolean ok;
        public String message;
    }

    public static class TgConfirm {
        public String reply;         // текст для чата Telegram
        public String playerId;
        public String playerName;
        public RewardData reward;    // если не null — выдать игроку сейчас (если онлайн) или при следующем входе
        public long activationId;    // id записи активации для отметки «награда выдана»
    }

    private static final Pattern CODE_REGEX = Pattern.compile("^[A-Za-z0-9_\\-]{3,32}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MyCodesCore core;
    private final Map<String, PromoCode> byId = new LinkedHashMap<String, PromoCode>();
    private final Map<String, PromoCode> byCode = new LinkedHashMap<String, PromoCode>();

    public PromoManager(MyCodesCore core) {
        this.core = core;
    }

    public synchronized void loadFromStorage() {
        byId.clear();
        byCode.clear();
        try {
            for (PromoCode p : core.storage().loadPromos()) {
                byId.put(p.id, p);
                byCode.put(p.codeKey(), p);
            }
        } catch (Exception e) {
            core.adapter().logError("Failed to load promos from storage", e);
        }
    }

    public synchronized List<PromoCode> all() {
        return new ArrayList<PromoCode>(byId.values());
    }

    public synchronized PromoCode byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public synchronized PromoCode byCode(String code) {
        return code == null ? null : byCode.get(code.toLowerCase());
    }

    // ── Создание / изменение / удаление ──

    public synchronized CreateResult create(PromoCode promo) {
        CreateResult result = new CreateResult();
        if (promo.code == null || !CODE_REGEX.matcher(promo.code).matches()) {
            result.message = core.lang().msg("create-invalid-code");
            return result;
        }
        if (byCode.containsKey(promo.code.toLowerCase())) {
            result.message = core.lang().msg("create-exists", "code", promo.code);
            return result;
        }
        promo.createdAt = System.currentTimeMillis();
        promo.usedCount = 0;
        try {
            core.storage().savePromo(promo);
        } catch (Exception e) {
            core.adapter().logError("Failed to save promo", e);
            result.message = core.lang().msg("storage-error");
            return result;
        }
        byId.put(promo.id, promo);
        byCode.put(promo.codeKey(), promo);
        result.ok = true;
        result.message = core.lang().msg("create-done", "code", promo.code);
        announceNewPromo(promo);
        return result;
    }

    public synchronized boolean update(PromoCode promo) {
        try {
            PromoCode old = byId.get(promo.id);
            if (old != null && !old.codeKey().equals(promo.codeKey())) {
                byCode.remove(old.codeKey());
            }
            core.storage().savePromo(promo);
            byId.put(promo.id, promo);
            byCode.put(promo.codeKey(), promo);
            return true;
        } catch (Exception e) {
            core.adapter().logError("Failed to update promo", e);
            return false;
        }
    }

    public synchronized boolean delete(String promoId) {
        PromoCode promo = byId.get(promoId);
        if (promo == null) return false;
        try {
            core.storage().deletePromo(promoId);
        } catch (Exception e) {
            core.adapter().logError("Failed to delete promo", e);
            return false;
        }
        byId.remove(promoId);
        byCode.remove(promo.codeKey());
        return true;
    }

    private void announceNewPromo(PromoCode promo) {
        // уведомление всем игрокам в игре
        if (core.config().broadcastOnCreate()) {
            core.adapter().broadcastLegacy(core.lang().msg("broadcast-new-promo",
                    "code", promo.code));
        }
        // уведомление админов в Telegram (привлечение игроков, см. config.yml)
        if (core.config().telegramNotifyOnCreate() && core.telegramBot() != null && core.telegramBot().isAvailable()) {
            final String text = core.lang().plain("tg-notify-new-promo",
                    "code", promo.code,
                    "type", core.lang().plain(promo.rewardType() == dev.kilo.mycodes.common.model.RewardType.ITEM ? "type-item" : "type-command"),
                    "server", promo.anyServer() ? core.lang().plain("server-any") : promo.server,
                    "uses", promo.isUnlimited() ? core.lang().plain("unlimited") : String.valueOf(promo.maxUses));
            core.adapter().async().execute(new Runnable() {
                @Override
                public void run() {
                    core.telegramBot().notifyAdmins(core.config().telegramAdminChats(), text);
                }
            });
        }
    }

    // ── Активация ──

    private boolean requiresTelegram(PromoCode promo) {
        String mode = promo.telegramMode == null ? "DEFAULT" : promo.telegramMode.toUpperCase();
        if ("REQUIRED".equals(mode)) return true;
        if ("OFF".equals(mode)) return false;
        return core.config().telegramRequireConfirmation();
    }

    private boolean telegramAvailable() {
        return core.telegramBot() != null && core.telegramBot().isAvailable();
    }

    public synchronized RedeemResult redeem(UUID playerId, String playerName, String currentServer, String code) {
        PromoCode promo = byCode(code);
        if (code == null || code.isEmpty()) {
            return RedeemResult.error(core.lang().msg("redeem-usage"));
        }
        if (promo == null) {
            return RedeemResult.error(prefix(core.lang().msg("redeem-not-found", "code", code)));
        }
        if (!promo.enabled) {
            return RedeemResult.error(prefix(core.lang().msg("redeem-disabled", "code", promo.code)));
        }
        long now = System.currentTimeMillis();
        if (promo.isExpired(now)) {
            return RedeemResult.error(prefix(core.lang().msg("redeem-expired", "code", promo.code)));
        }
        if (!promo.isUnlimited() && promo.usedCount >= promo.maxUses) {
            return RedeemResult.error(prefix(core.lang().msg("redeem-exhausted", "code", promo.code)));
        }
        if (!promo.anyServer() && currentServer != null
                && !promo.server.equalsIgnoreCase(currentServer)) {
            return RedeemResult.error(prefix(core.lang().msg("redeem-wrong-server",
                    "code", promo.code, "server", promo.server)));
        }
        try {
            if (promo.oncePerPlayer && core.storage().hasActivated(promo.id, playerId)) {
                return RedeemResult.error(prefix(core.lang().msg("redeem-already-used", "code", promo.code)));
            }
        } catch (Exception e) {
            core.adapter().logError("Storage failure during redeem", e);
            return RedeemResult.error(prefix(core.lang().msg("storage-error")));
        }

        boolean tgRequired = requiresTelegram(promo);
        if (tgRequired && !telegramAvailable()) {
            return RedeemResult.error(prefix(core.lang().msg("telegram-not-configured")));
        }
        if (tgRequired) {
            TgBinding binding = null;
            try {
                binding = core.storage().getBinding(playerId);
            } catch (Exception e) {
                core.adapter().logError("Storage failure during redeem", e);
            }
            if (binding != null) {
                // уже привязан — Telegram-аккаунт известен, можно выдавать сразу
                return finishActivation(promo, playerId, playerName, binding.telegramId);
            }
            return issuePendingLink(promo, playerId, playerName);
        }
        return finishActivation(promo, playerId, playerName, null);
    }

    private RedeemResult finishActivation(PromoCode promo, UUID playerId, String playerName, String telegramId) {
        try {
            if (promo.telegramOnce && telegramId != null
                    && core.storage().countByTelegram(promo.id, telegramId) > 0) {
                return RedeemResult.error(prefix(core.lang().msg("redeem-already-used-telegram", "code", promo.code)));
            }
            Activation activation = new Activation();
            activation.promoId = promo.id;
            activation.code = promo.code;
            activation.playerId = playerId.toString();
            activation.playerName = playerName;
            activation.telegramId = telegramId;
            activation.rewarded = false;
            core.storage().addActivation(activation);
            promo.usedCount++;
            core.storage().savePromo(promo);
            String message;
            if (promo.message != null && !promo.message.isEmpty()) {
                // кастомное сообщение: поддерживает градиенты, &-коды и плейсхолдеры
                String left = promo.isUnlimited()
                        ? core.lang().msg("unlimited")
                        : String.valueOf(Math.max(0, promo.maxUses - promo.usedCount));
                String max = promo.isUnlimited()
                        ? core.lang().msg("unlimited")
                        : String.valueOf(promo.maxUses);
                message = dev.kilo.mycodes.common.util.TextUtil.toLegacy(promo.message
                        .replace("%player%", playerName == null ? "?" : playerName)
                        .replace("%code%", promo.code)
                        .replace("%uses%", String.valueOf(promo.usedCount))
                        .replace("%left%", left)
                        .replace("%max%", max));
            } else {
                message = core.lang().msg("redeem-success", "code", promo.code);
            }
            RewardData reward = new RewardData(promo);
            reward.activationId = activation.id;
            return RedeemResult.success(message, promo.code, reward);
        } catch (Exception e) {
            core.adapter().logError("Storage failure during activation", e);
            return RedeemResult.error(prefix(core.lang().msg("storage-error")));
        }
    }

    private RedeemResult issuePendingLink(PromoCode promo, UUID playerId, String playerName) {
        try {
            PendingLink link = new PendingLink();
            byte[] bytes = new byte[12];
            RANDOM.nextBytes(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            link.token = sb.toString();
            link.promoId = promo.id;
            link.playerId = playerId.toString();
            link.playerName = playerName;
            link.createdAt = System.currentTimeMillis();
            link.expiresAt = link.createdAt + core.config().telegramLinkExpireMinutes() * 60_000L;
            core.storage().savePendingLink(link);
            String url = core.telegramBot().deepLink(link.token);
            String message = core.lang().msg("telegram-pending", "code", promo.code);
            return RedeemResult.pending(message, promo.code, url,
                    core.lang().msg("telegram-pending-link"));
        } catch (Exception e) {
            core.adapter().logError("Storage failure during link creation", e);
            return RedeemResult.error(prefix(core.lang().msg("storage-error")));
        }
    }

    // вызывается ботом: /start <token>
    public synchronized TgConfirm confirmTelegram(String token, long chatId) {
        TgConfirm out = new TgConfirm();
        try {
            PendingLink link = core.storage().getPendingLink(token);
            if (link == null || link.isExpired(System.currentTimeMillis())) {
                if (link != null) core.storage().deletePendingLink(token);
                out.reply = core.lang().plain("tg-link-expired");
                return out;
            }
            PromoCode promo = byId.get(link.promoId);
            if (promo == null || !promo.enabled) {
                core.storage().deletePendingLink(token);
                out.reply = core.lang().plain("tg-promo-gone");
                return out;
            }
            if (promo.isExpired(System.currentTimeMillis())
                    || (!promo.isUnlimited() && promo.usedCount >= promo.maxUses)) {
                core.storage().deletePendingLink(token);
                out.reply = core.lang().plain("tg-exhausted");
                return out;
            }
            // защита от мультиаккаунтов: один Telegram — один игрок
            TgBinding existing = core.storage().getBindingByTg(String.valueOf(chatId));
            if (existing != null && !existing.playerId.equals(link.playerId)) {
                if (core.config().telegramAntiMulti()) {
                    out.reply = core.lang().plain("tg-already-bound", "player", existing.playerName);
                    return out;
                }
            }
            if (promo.oncePerPlayer && core.storage().hasActivated(promo.id, link.playerUuid())) {
                core.storage().deletePendingLink(token);
                out.reply = core.lang().plain("tg-already-used");
                return out;
            }
            if (promo.telegramOnce && existing != null
                    && core.storage().countByTelegram(promo.id, String.valueOf(chatId)) > 0) {
                core.storage().deletePendingLink(token);
                out.reply = core.lang().plain("tg-already-used");
                return out;
            }
            if (existing == null) {
                TgBinding binding = new TgBinding();
                binding.playerId = link.playerId;
                binding.playerName = link.playerName;
                binding.telegramId = String.valueOf(chatId);
                core.storage().saveBinding(binding);
            }
            Activation activation = new Activation();
            activation.promoId = promo.id;
            activation.code = promo.code;
            activation.playerId = link.playerId;
            activation.playerName = link.playerName;
            activation.telegramId = String.valueOf(chatId);
            activation.rewarded = false;
            core.storage().addActivation(activation);
            promo.usedCount++;
            core.storage().savePromo(promo);
            core.storage().deletePendingLink(token);
            out.reply = core.lang().plain("tg-confirmed", "code", promo.code);
            out.playerId = link.playerId;
            out.playerName = link.playerName;
            out.reward = new RewardData(promo);
            out.activationId = activation.id;
            return out;
        } catch (Exception e) {
            core.adapter().logError("Storage failure during telegram confirm", e);
            out.reply = core.lang().plain("tg-error");
            return out;
        }
    }

    // ── Невыданные награды (для выдачи при следующем входе) ──

    public synchronized List<Activation> outstandingRewards(UUID playerId) {
        try {
            return core.storage().outstandingRewards(playerId);
        } catch (Exception e) {
            core.adapter().logError("Storage failure loading outstanding rewards", e);
            return new ArrayList<Activation>();
        }
    }

    public synchronized RewardData rewardFor(Activation activation) {
        PromoCode promo = byId.get(activation.promoId);
        return promo == null ? RewardData.none() : new RewardData(promo);
    }

    public synchronized void markRewarded(Activation activation) {
        try {
            core.storage().markRewarded(activation);
        } catch (Exception e) {
            core.adapter().logError("Storage failure marking reward", e);
        }
    }

    public synchronized void markRewardedById(long activationId) {
        try {
            core.storage().markRewardedById(activationId);
        } catch (Exception e) {
            core.adapter().logError("Storage failure marking reward", e);
        }
    }

    /** Выдать все невыданные награды игрока (вызывается при входе/смене сервера) */
    public synchronized int deliverOutstanding(UUID playerId, String playerName,
                                               dev.kilo.mycodes.common.RewardDispatcher dispatcher) {
        List<Activation> outstanding = outstandingRewards(playerId);
        int delivered = 0;
        for (Activation activation : outstanding) {
            RewardData reward = rewardFor(activation);
            if (dispatcher.dispatch(playerId, playerName, null, reward)) {
                markRewarded(activation);
                delivered++;
            }
        }
        return delivered;
    }

    // ── Вспомогательное ──

    /** Сообщения lang уже содержат %prefix% (он подставляется автоматически),
     *  поэтому здесь ничего не добавляется. */
    private String prefix(String message) {
        return message;
    }

    public String formatDate(long epochMs) {
        return new SimpleDateFormat(core.config().dateFormat())
                .format(new Date(epochMs));
    }
}
