package dev.kilo.mycodes.common;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Абстракция платформы (BungeeCord, Velocity, Spigot).
 * Ядро плагина ничего не знает о конкретном серверном API и общается
 * с платформой исключительно через этот интерфейс.
 */
public interface PlatformAdapter {

    String platformName();

    /** true для BungeeCord/Waterfall/Velocity, false для одиночного Spigot-сервера */
    boolean isProxy();

    Path dataFolder();

    void logInfo(String message);

    void logWarn(String message);

    void logError(String message, Throwable t);

    /** Пул потоков для фоновых задач ядра (хранилище, Telegram) */
    Executor async();

    /** Имена серверов сети (для меню выбора сервера). На одиночном сервере — пусто. */
    List<String> serverNames();

    /** Отправить сообщение (legacy-текст с градиентами) всем игрокам платформы */
    void broadcastLegacy(String legacyText);
}
