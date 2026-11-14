package dev.kilo.mycodes.common.model;

import java.util.UUID;

/**
 * Запись об активации промокода игроком.
 */
public class Activation {

    public long id;
    public String promoId;
    public String code = "";
    public String playerId = "";
    public String playerName = "";
    public long time = System.currentTimeMillis();
    /** Telegram chat id подтвердившего аккаунта или null */
    public String telegramId = null;
    /** Выдана ли награда (false = выдать при следующем входе) */
    public boolean rewarded = false;

    public UUID playerUuid() {
        try {
            return UUID.fromString(playerId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
