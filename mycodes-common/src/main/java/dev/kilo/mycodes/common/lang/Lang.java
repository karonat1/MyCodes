package dev.kilo.mycodes.common.lang;

import dev.kilo.mycodes.common.PlatformAdapter;
import dev.kilo.mycodes.common.config.YamlCommentPatcher;
import dev.kilo.mycodes.common.util.TextUtil;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Локализация: en (по умолчанию), ru, tr. Файлы выгружаются в
 * plugins/MyCodes/lang/<код>.yml и патчатся с сохранением комментариев.
 * Смена языка применяется через /mcd reload.
 *
 * <p>В текстах поддерживаются градиенты: {@code <gradient:#00E5FF:#7C4DFF>текст</gradient>}
 * и стандартные &-коды цветов.
 */
public class Lang {

    private final PlatformAdapter adapter;
    private final Path langFolder;
    private final String[] fallbackOrder;
    private Configuration user;
    private Configuration embedded;   // тот же язык из jar
    private Configuration english;    // en из jar
    private String code = "en";

    public Lang(PlatformAdapter adapter, Path dataFolder, String requestedCode) {
        this.adapter = adapter;
        this.langFolder = dataFolder.resolve("lang");
        this.fallbackOrder = order(requestedCode);
    }

    private static String[] order(String code) {
        if (code == null || code.isEmpty()) code = "en";
        code = code.toLowerCase();
        if (code.equals("en")) return new String[]{"en"};
        return new String[]{code, "en"};
    }

    public String code() {
        return code;
    }

    public void load(String requestedCode) throws IOException {
        this.code = (requestedCode == null || requestedCode.isEmpty())
                ? "en" : requestedCode.toLowerCase();
        this.prefixCache = null;
        this.embedded = loadEmbedded(code);
        this.english = code.equals("en") ? embedded : loadEmbedded("en");
        Files.createDirectories(langFolder);
        // выгружаем все языковые файлы сразу — их удобно править заранее
        for (String known : new String[]{"en", "ru", "tr"}) {
            Path knownFile = langFolder.resolve(known + ".yml");
            if (!Files.exists(knownFile)) {
                try {
                    String textForFile = readResourceText("/lang/" + known + ".yml");
                    if (!textForFile.isEmpty()) {
                        Files.write(knownFile, textForFile.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    adapter.logWarn("lang/" + known + ".yml: cannot extract: " + e.getMessage());
                }
            }
        }
        Path file = langFolder.resolve(code + ".yml");
        String embeddedText = readResourceText("/lang/" + code + ".yml");
        if (!Files.exists(file)) {
            Files.write(file, embeddedText.getBytes(StandardCharsets.UTF_8));
        }
        String userText = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        // патчим комментарии, как у config.yml
        try {
            String patched = YamlCommentPatcher.patch(userText, embeddedText);
            if (patched != null) {
                Files.write(file, patched.getBytes(StandardCharsets.UTF_8));
                userText = patched;
            }
        } catch (Exception e) {
            adapter.logWarn("lang/" + code + ".yml: comment patch failed: " + e.getMessage());
        }
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        try {
            user = provider.load(new StringReader(userText));
        } catch (Exception e) {
            adapter.logWarn("lang/" + code + ".yml: broken file, using embedded texts.");
            user = null;
        }
    }

    private Configuration loadEmbedded(String code) {
        String text = readResourceText("/lang/" + code + ".yml");
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new StringReader(text));
        } catch (Exception e) {
            return new Configuration();
        }
    }

    private String readResourceText(String path) {
        InputStream in = Lang.class.getResourceAsStream(path);
        if (in == null) return "";
        try {
            Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A");
            String text = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            return text;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private Object raw(String key) {
        if (user != null) {
            Object v = user.get(key);
            if (v != null) return v;
        }
        if (embedded != null) {
            Object v = embedded.get(key);
            if (v != null) return v;
        }
        if (english != null) {
            Object v = english.get(key);
            if (v != null) return v;
        }
        return null;
    }

    /** рендер префикса из ключа prefix (с подстановкой градиентов) */
    private String renderPrefix(boolean plain) {
        // может содержать %prefix% сам в себе? нет — просто рендерим
        String rendered = prefixCache != null ? prefixCache : null;
        if (rendered == null) {
            Object v = raw("prefix");
            rendered = v == null ? "" : String.valueOf(v);
            // защита от циклической подстановки
            if (rendered.contains("%prefix%")) {
                rendered = rendered.replace("%prefix%", "");
            }
            prefixCachePlain = TextUtil.strip(rendered);
            prefixCache = TextUtil.toLegacy(rendered);
        }
        return plain ? prefixCachePlain : prefixCache;
    }

    private String prefixCache;
    private String prefixCachePlain;

    private String apply(Object template, boolean plain, String... kv) {
        if (template == null) return "";
        String s = String.valueOf(template);
        s = s.replace("%prefix%", renderPrefix(plain));
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                s = s.replace("%" + kv[i] + "%", kv[i + 1] == null ? "" : kv[i + 1]);
            }
        }
        // общие плейсхолдеры
        s = s.replace("\\n", "\n");
        return s;
    }

    /** Сообщение с градиентами, готовое к отправке (legacy) */
    public String msg(String key, String... kv) {
        Object v = raw(key);
        if (v == null) return TextUtil.toLegacy(key);
        return renderMulti(v, false, kv);
    }

    private String renderMulti(Object v, boolean plain, String... kv) {
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object line : (List<?>) v) {
                if (sb.length() > 0) sb.append('\n');
                String applied = apply(line, plain, kv);
                sb.append(plain ? TextUtil.strip(applied) : TextUtil.toLegacy(applied));
            }
            return sb.toString();
        }
        String applied = apply(v, plain, kv);
        return plain ? TextUtil.strip(applied) : TextUtil.toLegacy(applied);
    }

    /** Список строк с плейсхолдерами (для построчной отправки) */
    public List<String> msgList(String key, String... kv) {
        List<String> out = new ArrayList<String>();
        Object v = raw(key);
        if (v instanceof List) {
            for (Object line : (List<?>) v) {
                out.add(TextUtil.toLegacy(apply(line, false, kv)));
            }
        } else if (v != null) {
            out.add(TextUtil.toLegacy(apply(v, false, kv)));
        }
        return out;
    }

    /** Чистый текст для Telegram и консоли */
    public String plain(String key, String... kv) {
        Object v = raw(key);
        return v == null ? key : renderMulti(v, true, kv);
    }

    /** Сырой шаблон без обработки (для вложенных подстановок) */
    public String template(String key) {
        Object v = raw(key);
        return v == null ? "" : String.valueOf(v);
    }
}
