package dev.kilo.mycodes.common.model;

/**
 * Привязка игрока к Telegram-аккаунту (защита от мультиаккаунтов:
 * один Telegram = один игрок).
 */
public class TgBinding {

    public String playerId;
    public String playerName = "";
    public String telegramId = "";
    public long boundAt = System.currentTimeMillis();
}
