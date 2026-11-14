package dev.kilo.mycodes.common.model;

/**
 * Тип награды промокода.
 */
public enum RewardType {

    /** Команда, выполняемая от имени консоли; %player% заменяется на ник игрока */
    COMMAND,

    /** Предмет, выдаваемый игроку (сериализован в base64) */
    ITEM;

    public static RewardType of(String s) {
        if (s == null) return COMMAND;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMAND;
        }
    }
}
