package dev.kilo.mycodes.common.config;

import dev.kilo.mycodes.common.PlatformAdapter;
import dev.kilo.mycodes.common.model.EffectType;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Загрузка config.yml с сохранением комментариев: новые ключи из дефолтного
 * конфига аккуратно дозаписываются в пользовательский файл вместе с их
 * комментариями. Любые изменения применяются через /mcd reload.
 */
public class ConfigManager {

    private final PlatformAdapter adapter;
    private final Path dataFolder;
    private Configuration config;
    private Configuration defaults;

    public ConfigManager(PlatformAdapter adapter, Path dataFolder) {
        this.adapter = adapter;
        this.dataFolder = dataFolder;
    }

    public void load() throws IOException {
        Files.createDirectories(dataFolder);
        Path file = dataFolder.resolve("config.yml");
        String defaultText = readResource("/config.yml");
        if (!Files.exists(file)) {
            Files.write(file, defaultText.getBytes(StandardCharsets.UTF_8));
        }
        String userText = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        defaults = provider.load(new StringReader(defaultText));
        Configuration user = provider.load(new StringReader(userText));
        config = user;

        Set<String> defPaths = collectPaths(defaults, "");
        Set<String> userPaths = collectPaths(user, "");
        List<String> missing = new ArrayList<String>();
        for (String p : defPaths) {
            if (!userPaths.contains(p)) missing.add(p);
        }
        if (!missing.isEmpty()) {
            String patched = YamlCommentPatcher.patch(userText, defaultText);
            if (patched != null) {
                try {
                    Files.write(file, patched.getBytes(StandardCharsets.UTF_8));
                    config = provider.load(new StringReader(patched));
                    adapter.logInfo("config.yml: added " + missing.size() + " new option(s), comments preserved.");
                } catch (Exception e) {
                    adapter.logWarn("config.yml: failed to write updated config: " + e.getMessage());
                }
            }
            // добиваем значения в памяти, если патч не покрыл всё
            Set<String> afterPaths = collectPaths(config, "");
            for (String p : defPaths) {
                if (!afterPaths.contains(p)) {
                    config.set(p, defaults.get(p));
                }
            }
        }
    }

    private Set<String> collectPaths(Configuration section, String prefix) {
        Set<String> out = new LinkedHashSet<String>();
        for (String key : section.getKeys()) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object v = section.get(key);
            if (v == null) continue;
            out.add(path);
            if (v instanceof Configuration) {
                out.addAll(collectPaths((Configuration) v, path));
            }
        }
        return out;
    }

    private String readResource(String path) throws IOException {
        InputStream in = ConfigManager.class.getResourceAsStream(path);
        if (in == null) throw new IOException("Resource not found: " + path);
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

    // ── Typed getters ──

    private Object raw(String path) {
        Object v = config.get(path);
        if (v == null) v = defaults != null ? defaults.get(path) : null;
        return v;
    }

    public String language() {
        String v = String.valueOf(raw("language"));
        return v == null || "null".equals(v) ? "en" : v.toLowerCase();
    }

    public String storageType() {
        String v = String.valueOf(raw("storage.type"));
        return v == null || "null".equals(v) ? "file" : v.toLowerCase();
    }

    public String mysqlHost()       { return str("storage.mysql.host", "localhost"); }
    public int mysqlPort()         { return num("storage.mysql.port", 3306); }
    public String mysqlDatabase()  { return str("storage.mysql.database", "mycodes"); }
    public String mysqlUser()      { return str("storage.mysql.username", "root"); }
    public String mysqlPassword()  { return str("storage.mysql.password", ""); }
    public String mysqlPrefix()    { return str("storage.mysql.table-prefix", "mycodes_"); }
    public boolean mysqlSsl()       { return bool("storage.mysql.use-ssl", false); }

    public boolean telegramEnabled()          { return bool("telegram.enabled", false); }
    public String telegramToken()             { return str("telegram.bot-token", ""); }
    public List<String> telegramAdminChats() {
        String raw = str("telegram.admin-chat-id", "");
        List<String> out = new ArrayList<String>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
    public boolean telegramRequireConfirmation() { return bool("telegram.require-confirmation", false); }
    public boolean telegramAntiMulti()          { return bool("telegram.anti-multi", true); }
    public boolean telegramNotifyOnCreate()      { return bool("telegram.notify-on-create", true); }
    public int telegramPollTimeout()             { return num("telegram.poll-timeout", 30); }
    public int telegramLinkExpireMinutes()       { return num("telegram.link-expire-minutes", 15); }

    public EffectType defaultEffect() {
        return EffectType.of(str("effects.default-type", "FIREWORK"));
    }
    public int defaultEffectDuration() { return clamp(num("effects.default-duration", 3), 1, 10); }
    public int defaultRewardDelay()   { return clamp(num("effects.default-reward-delay", 0), 0, 60); }
    public boolean effectSounds()     { return bool("effects.sounds", true); }

    public boolean broadcastOnCreate() { return bool("broadcast-on-create", true); }

    public String dateFormat() { return str("date-format", "dd.MM.yyyy HH:mm"); }

    private String str(String path, String def) {
        Object v = raw(path);
        return v == null ? def : String.valueOf(v);
    }

    private int num(String path, int def) {
        Object v = raw(path);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private boolean bool(String path, boolean def) {
        Object v = raw(path);
        if (v instanceof Boolean) return (Boolean) v;
        String s = String.valueOf(v);
        return "true".equalsIgnoreCase(s);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public String rawString(String path, String def) {
        return str(path, def);
    }
}
