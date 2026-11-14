package dev.kilo.mycodes.common;

import java.util.UUID;

/**
 * Абстракция отправителя команд (игрок или консоль) для общего сервиса команд.
 */
public interface PlatformSender {

    /** UUID игрока или null для консоли */
    UUID uuid();

    String name();

    boolean isPlayer();

    boolean hasPermission(String permission);

    void sendLegacy(String legacyText);

    /** Отправить кликабельную ссылку (текст → url) */
    void sendLink(String legacyText, String url);
}
