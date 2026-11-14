package dev.kilo.mycodes.common;

/**
 * Показ текста поверх экрана игрока (заголовок по центру + actionbar-полоса).
 * Реализуется платформой; метод — только для игроков.
 */
public interface TitleSender {

    /**
     * Большой текст по центру экрана.
     *
     * @param title      верхняя строка (legacy-текст)
     * @param subtitle   нижняя строка или null
     * @param fadeIn     время появления, тиков (20 = 1 сек)
     * @param stay       время удержания
     * @param fadeOut     время исчезания
     */
    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /** Полоска над хотбаром (actionbar) */
    void sendActionBar(String legacyText);
}
