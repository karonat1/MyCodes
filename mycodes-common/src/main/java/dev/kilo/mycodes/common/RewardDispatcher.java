package dev.kilo.mycodes.common;

import dev.kilo.mycodes.common.model.RewardData;

import java.util.UUID;

/**
 * Выдача награды с эффектом. Реализуется платформой:
 * Spigot выдаёт локально, прокси отправляет по мосту на backend
 * (или выполняет команду в консоли прокси, если моста нет).
 */
public interface RewardDispatcher {

    /**
     * Выдать награду игроку (если он онлайн).
     *
     * @param server целевой сервер или null — текущий
     * @return true — если награда реально выдана (можно отмечать в хранилище)
     */
    boolean dispatch(UUID playerId, String playerName, String server, RewardData reward);
}
