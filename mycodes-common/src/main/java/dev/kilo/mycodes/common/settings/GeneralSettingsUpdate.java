package dev.kilo.mycodes.common.settings;

/**
 * Изменение общих настроек из меню (язык, эффекты по умолчанию, оповещения).
 * Null = не менять. Сериализуется в JSON и передаётся по мосту.
 */
public class GeneralSettingsUpdate {
    /** en | ru | tr */
    public String language;
    public String defaultEffect;
    public Integer defaultDuration;
    public Integer defaultDelay;
    public Boolean sounds;
    public Boolean broadcastOnCreate;
}
