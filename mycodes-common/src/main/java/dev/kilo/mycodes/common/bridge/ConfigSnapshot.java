package dev.kilo.mycodes.common.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок настроек прокси, рассылается на backend-серверы (сообщение CONFIG).
 * Backend использует его для меню и локализации языка сети.
 */
public class ConfigSnapshot {

    public String proxyType = "unknown";
    public String version = "1.0.0";
    public List<String> servers = new ArrayList<String>();
    public String language = "en";
    public String dateFormat = "dd.MM.yyyy HH:mm";

    // эффекты по умолчанию
    public String defaultEffect = "FIREWORK";
    public int defaultDuration = 3;
    public int defaultRewardDelay = 0;
    public boolean sounds = true;
    public boolean broadcastOnCreate = true;

    // telegram
    public boolean telegramEnabled = false;
    public boolean telegramConnected = false;
    public String telegramUsername = "";
    public boolean telegramRequire = false;
    public boolean telegramAntiMulti = true;
    public boolean telegramNotify = true;
    public String telegramChatId = "";

    public ConfigSnapshot copyFromConfig(dev.kilo.mycodes.common.MyCodesCore core) {
        proxyType = core.adapter().platformName();
        version = "1.0.0";
        servers = new ArrayList<String>(core.adapter().serverNames());
        language = core.config().language();
        dateFormat = core.config().dateFormat();
        defaultEffect = core.config().defaultEffect().name();
        defaultDuration = core.config().defaultEffectDuration();
        defaultRewardDelay = core.config().defaultRewardDelay();
        sounds = core.config().effectSounds();
        broadcastOnCreate = core.config().broadcastOnCreate();
        telegramEnabled = core.config().telegramEnabled();
        telegramConnected = core.telegramBot() != null && core.telegramBot().isAvailable();
        telegramUsername = core.telegramBot() != null ? core.telegramBot().username() : "";
        telegramRequire = core.config().telegramRequireConfirmation();
        telegramAntiMulti = core.config().telegramAntiMulti();
        telegramNotify = core.config().telegramNotifyOnCreate();
        telegramChatId = String.join(",", core.config().telegramAdminChats());
        return this;
    }
}
