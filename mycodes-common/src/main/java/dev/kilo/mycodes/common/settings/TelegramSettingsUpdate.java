package dev.kilo.mycodes.common.settings;

/**
 * Изменение Telegram-настроек из меню. Null = не менять.
 * Сериализуется в JSON и передаётся по мосту на прокси.
 */
public class TelegramSettingsUpdate {
    public Boolean enabled;
    /** Новый токен бота (ключ от @BotFather) или null */
    public String token;
    public String chatId;
    public Boolean require;
    public Boolean antiMulti;
    public Boolean notify;
}
