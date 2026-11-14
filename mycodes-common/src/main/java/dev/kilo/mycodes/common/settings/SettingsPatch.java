package dev.kilo.mycodes.common.settings;

import dev.kilo.mycodes.common.config.YamlCommentPatcher;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Применение настроек из меню к config.yml с СОХРАНЕНИЕМ КОММЕНТАРИЕВ:
 * заменяются только строки значений. Общий код для прокси и backend.
 */
public final class SettingsPatch {

    private SettingsPatch() {
    }

    public static void applyTelegram(Path configFile, TelegramSettingsUpdate u) throws Exception {
        String text = read(configFile);
        if (u.enabled != null) {
            text = set(text, "telegram.enabled", u.enabled);
        }
        if (u.token != null) {
            text = set(text, "telegram.bot-token", u.token);
        }
        if (u.chatId != null) {
            text = set(text, "telegram.admin-chat-id", u.chatId);
        }
        if (u.require != null) {
            text = set(text, "telegram.require-confirmation", u.require);
        }
        if (u.antiMulti != null) {
            text = set(text, "telegram.anti-multi", u.antiMulti);
        }
        if (u.notify != null) {
            text = set(text, "telegram.notify-on-create", u.notify);
        }
        write(configFile, text);
    }

    public static void applyGeneral(Path configFile, GeneralSettingsUpdate u) throws Exception {
        String text = read(configFile);
        if (u.language != null) {
            text = set(text, "language", u.language);
        }
        if (u.defaultEffect != null) {
            text = set(text, "effects.default-type", u.defaultEffect);
        }
        if (u.defaultDuration != null) {
            text = set(text, "effects.default-duration", u.defaultDuration.intValue());
        }
        if (u.defaultDelay != null) {
            text = set(text, "effects.default-reward-delay", u.defaultDelay.intValue());
        }
        if (u.sounds != null) {
            text = set(text, "effects.sounds", u.sounds);
        }
        if (u.broadcastOnCreate != null) {
            text = set(text, "broadcast-on-create", u.broadcastOnCreate);
        }
        write(configFile, text);
    }

    private static String set(String text, String path, boolean value) throws Exception {
        return setRendered(text, path, YamlCommentPatcher.renderScalar(value));
    }

    private static String set(String text, String path, int value) throws Exception {
        return setRendered(text, path, YamlCommentPatcher.renderScalar(value));
    }

    private static String set(String text, String path, String value) throws Exception {
        return setRendered(text, path, YamlCommentPatcher.renderScalar(value));
    }

    private static String setRendered(String text, String path, String rendered) throws Exception {
        String patched = YamlCommentPatcher.setValue(text, path, rendered);
        if (patched != null) return patched;
        // ключ не найден в тексте — добавим через API (комментарии в этой ситуации
        // сохранит следующий patchMissingKeys)
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        net.md_5.bungee.config.Configuration cfg = provider.load(new StringReader(text));
        cfg.set(path, rawValue(rendered));
        java.io.StringWriter writer = new java.io.StringWriter();
        provider.save(cfg, writer);
        return writer.toString();
    }

    private static Object rawValue(String rendered) {
        if (rendered.startsWith("'") && rendered.endsWith("'") && rendered.length() >= 2) {
            return rendered.substring(1, rendered.length() - 1).replace("''", "'");
        }
        if ("true".equals(rendered)) return Boolean.TRUE;
        if ("false".equals(rendered)) return Boolean.FALSE;
        try {
            return Integer.valueOf(rendered);
        } catch (NumberFormatException e) {
            return rendered;
        }
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void write(Path file, String text) throws Exception {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
