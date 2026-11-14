package dev.kilo.mycodes.common.model;

/**
 * Десять эффектов активации промокода. Реализация (частицы, звуки)
 * находится в Spigot-модуле, воспроизводится там, где находится игрок.
 */
public enum EffectType {

    FIREWORK,
    FIREWORK_RAIN,
    HELIX,
    DOME,
    TOTEM,
    HEARTS,
    ENCHANT,
    EXPLOSION,
    WATER,
    MYSTIC,
    LASER,
    FAIRY,
    DRAGON,
    PULSAR;

    public static EffectType of(String s) {
        if (s == null) return FIREWORK;
        for (EffectType t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return FIREWORK;
    }
}
