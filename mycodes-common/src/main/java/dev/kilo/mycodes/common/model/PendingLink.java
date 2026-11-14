package dev.kilo.mycodes.common.model;

import java.util.UUID;

/**
 * Ожидание подтверждения промокода через Telegram.
 * Игрок получает ссылку t.me/<bot>?start=<token> — подтверждение в боте
 * активирует промокод автоматически.
 */
public class PendingLink {

    public String token;
    public String promoId;
    public String playerId;
    public String playerName = "";
    public long createdAt = System.currentTimeMillis();
    public long expiresAt;

    public UUID playerUuid() {
        try {
            return UUID.fromString(playerId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && expiresAt < now;
    }
}
