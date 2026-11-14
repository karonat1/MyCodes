package dev.kilo.mycodes.common.storage;

import dev.kilo.mycodes.common.model.Activation;
import dev.kilo.mycodes.common.model.PendingLink;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.TgBinding;

import java.util.List;
import java.util.UUID;

/**
 * Хранилище промокодов, активаций и Telegram-привязок.
 * Реализации: JSON-файлы (по умолчанию) и MySQL.
 */
public interface Storage {

    interface Factory {
        Storage create();
    }

    void init() throws Exception;

    void close();

    // ── Промокоды ──
    List<PromoCode> loadPromos();

    void savePromo(PromoCode promo) throws Exception;

    void deletePromo(String promoId) throws Exception;

    // ── Активации ──
    boolean hasActivated(String promoId, UUID playerId) throws Exception;

    int countByTelegram(String promoId, String telegramId) throws Exception;

    void addActivation(Activation activation) throws Exception;

    /** Активации, чья награда ещё не выдана (выдаются при следующем входе) */
    List<Activation> outstandingRewards(UUID playerId) throws Exception;

    void markRewarded(Activation activation) throws Exception;

    void markRewardedById(long activationId) throws Exception;

    // ── Telegram-привязки ──
    TgBinding getBinding(UUID playerId) throws Exception;

    TgBinding getBindingByTg(String telegramId) throws Exception;

    void saveBinding(TgBinding binding) throws Exception;

    // ── Ожидающие подтверждения ссылки ──
    void savePendingLink(PendingLink link) throws Exception;

    PendingLink getPendingLink(String token) throws Exception;

    void deletePendingLink(String token) throws Exception;

    void cleanExpiredPendingLinks(long now) throws Exception;
}
