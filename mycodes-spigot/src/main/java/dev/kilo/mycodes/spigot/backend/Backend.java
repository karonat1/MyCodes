package dev.kilo.mycodes.spigot.backend;

import dev.kilo.mycodes.common.model.EffectType;
import dev.kilo.mycodes.common.model.PromoCode;
import dev.kilo.mycodes.common.model.RedeemResult;
import dev.kilo.mycodes.common.settings.GeneralSettingsUpdate;
import dev.kilo.mycodes.common.settings.TelegramSettingsUpdate;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Единый фасад данных и действий для GUI: локальный (одиночный сервер)
 * или удалённый через мост на прокси.
 */
public interface Backend {

    boolean isRemote();

    /** Название режима для меню: «BungeeCord (сеть)» или «одиночный сервер» */
    String platformLabel();

    List<PromoCode> promos();

    /** Список серверов сети (пустой = одиночный режим) */
    List<String> servers();

    // ── Текущие настройки (для отображения в меню) ──
    String language();

    EffectType defaultEffect();

    int defaultEffectDuration();

    int defaultRewardDelay();

    boolean effectSounds();

    boolean broadcastOnCreate();

    boolean telegramEnabled();

    boolean telegramConnected();

    String telegramUsername();

    boolean telegramRequire();

    boolean telegramAntiMulti();

    boolean telegramNotify();

    String telegramChatId();

    // ── Действия. feedback — готовое сообщение (успех/ошибка) ──

    /** Запросить свежие данные у прокси (в remote-режиме) */
    void refreshAsync();

    void create(PromoCode promo, Consumer<String> feedback);

    void update(PromoCode promo, Consumer<String> feedback);

    void delete(String promoId, String code, Consumer<String> feedback);

    void updateGeneral(GeneralSettingsUpdate update, Consumer<String> feedback);

    void updateTelegram(TelegramSettingsUpdate update, Consumer<String> feedback);

    void reload(Consumer<String> feedback);

    /** Активация промокода игроком. Callback вызывается на главном потоке. */
    void redeem(Player player, String code, Consumer<RedeemResult> callback);
}
