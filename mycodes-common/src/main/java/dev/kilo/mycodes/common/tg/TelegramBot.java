package dev.kilo.mycodes.common.tg;

import dev.kilo.mycodes.common.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Telegram-бот MyCodes.
 *
 * <p>Назначение (см. комментарий у telegram.bot-token в config.yml):
 * <ul>
 *   <li>Подтверждение активации промокодов по персональной ссылке
 *       t.me/<bot>?start=<token> — игрок жмёт Start, промокод
 *       активируется автоматически;</li>
 *   <li>Защита от мультиаккаунтов — привязка Telegram-аккаунта к игроку;</li>
 *   <li>Уведомления о новых промокодах в админский чат.</li>
 * </ul>
 */
public class TelegramBot {

    public interface Listener {
        /** /start <token> от игрока: подтвердить и вернуть текст ответа боту */
        String onConfirm(String token, long chatId);

        /** текст любого другого сообщения/команды; вернуть ответ или null */
        String onCommand(long chatId, String text);
    }

    private static final String API = "https://api.telegram.org/bot";

    private final String token;
    private final int pollTimeout;
    private final Listener listener;
    private final Log LOG;

    private volatile boolean running;
    private volatile String botUsername;
    private Thread thread;
    private long offset;

    /** логгер, чтобы не тянуть PlatformAdapter напрямую */
    public interface Log {
        void info(String s);
        void warn(String s);
        void error(String s);
    }

    public TelegramBot(String token, int pollTimeout, Listener listener, Log log) {
        this.token = token == null ? "" : token.trim();
        this.pollTimeout = Math.max(5, pollTimeout);
        this.listener = listener;
        this.LOG = log;
    }

    /** true, если бот запущен и отвечает */
    public boolean isAvailable() {
        return running && botUsername != null;
    }

    public String username() {
        return botUsername;
    }

    /** Запускает бота: проверяет ключ через getMe, снимает webhook, стартует long-polling */
    public boolean start() {
        if (token.isEmpty()) {
            LOG.warn("Telegram bot-token is empty. Set telegram.bot-token in config.yml (see comments there).");
            return false;
        }
        try {
            Map<String, String> me = call("getMe");
            if (me == null) {
                LOG.error("Telegram bot failed to start: token is invalid or Telegram unreachable.");
                return false;
            }
            botUsername = me.get("username");
            // если у бота висит webhook — убираем, иначе long-polling не работает
            call("deleteWebhook");
            LOG.info("Telegram bot connected: @" + botUsername);
        } catch (Exception e) {
            LOG.error("Telegram bot failed to start: " + e.getMessage());
            return false;
        }
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                pollLoop();
            }
        }, "MyCodes-Telegram");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                HttpURLConnection conn = open("getUpdates");
                writeForm(conn, form("timeout", String.valueOf(pollTimeout),
                        "offset", String.valueOf(offset), "allowed_updates", "[\"message\"]"));
                String body = read(conn);
                if (body == null) {
                    sleep(pollTimeout * 1000L);
                    continue;
                }
                com.google.gson.JsonObject json = JsonUtil.parseObject(body);
                if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
                    sleep(5000);
                    continue;
                }
                com.google.gson.JsonArray updates = json.getAsJsonArray("result");
                if (updates.size() == 0) continue;
                for (int i = 0; i < updates.size(); i++) {
                    com.google.gson.JsonObject update = updates.get(i).getAsJsonObject();
                    long id = update.get("update_id").getAsLong();
                    if (id >= offset) offset = id + 1;
                    handleUpdate(update);
                }
                if (Thread.currentThread().isInterrupted()) break;
            } catch (Exception e) {
                if (running) {
                    LOG.warn("Telegram poll error: " + e.getMessage());
                    sleep(10_000L);
                }
            }
        }
    }

    private void handleUpdate(com.google.gson.JsonObject update) {
        if (!update.has("message")) return;
        com.google.gson.JsonObject message = update.getAsJsonObject("message");
        if (!message.has("text")) return;
        String text = message.get("text").getAsString().trim();
        long chatId = message.getAsJsonObject("chat").get("id").getAsLong();
        try {
            String reply;
            if (text.toLowerCase().startsWith("/start")) {
                String payload = "";
                int sp = text.indexOf(' ');
                if (sp > 0) payload = text.substring(sp + 1).trim();
                reply = payload.isEmpty()
                        ? listener.onCommand(chatId, "/start")
                        : listener.onConfirm(payload, chatId);
            } else {
                reply = listener.onCommand(chatId, text);
            }
            if (reply != null && !reply.isEmpty()) {
                sendMessage(chatId, reply);
            }
        } catch (Exception e) {
            LOG.error("Telegram update handler error: " + e);
            sendMessage(chatId, "MyCodes: internal error. Try again later.");
        }
    }

    /** Отправить текстовое сообщение в чат (plain text, до 4000 символов) */
    public boolean sendMessage(long chatId, String plainText) {
        if (plainText == null) return false;
        String text = plainText.length() > 4000 ? plainText.substring(0, 4000) : plainText;
        try {
            return call("sendMessage", "chat_id", String.valueOf(chatId), "text", text) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Уведомить админские чаты */
    public void notifyAdmins(List<String> adminChatIds, String plainText) {
        for (String id : adminChatIds) {
            try {
                sendMessage(Long.parseLong(id.trim()), plainText);
            } catch (NumberFormatException ignored) {
                LOG.warn("Invalid telegram.admin-chat-id entry: " + id);
            }
        }
    }

    // ── HTTP ──

    private Map<String, String> call(String method, String... kv) throws IOException {
        HttpURLConnection conn = open(method);
        writeForm(conn, form(kv));
        return parseMe(read(conn));
    }

    private Map<String, String> call(String method) throws IOException {
        HttpURLConnection conn = open(method);
        writeForm(conn, form());
        return parseMe(read(conn));
    }

    private Map<String, String> parseMe(String body) {
        if (body == null) return null;
        com.google.gson.JsonObject json = JsonUtil.parseObject(body);
        if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
            if (json.has("description")) {
                LOG.warn("Telegram API error: " + json.get("description").getAsString());
            }
            return null;
        }
        if (json.has("result") && json.get("result").isJsonObject()) {
            com.google.gson.JsonObject r = json.getAsJsonObject("result");
            Map<String, String> me = new LinkedHashMap<String, String>();
            if (r.has("username")) me.put("username", r.get("username").getAsString());
            return me;
        }
        return new LinkedHashMap<String, String>();
    }

    private HttpURLConnection open(String method) throws IOException {
        URL url = new URL(API + token + "/" + method);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        if (method.equals("getUpdates")) {
            // long-polling запрос — читаем дольше
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout((pollTimeout + 15) * 1000);
        } else {
            conn.setReadTimeout(15_000);
        }
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        return conn;
    }

    private String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; kv != null && i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            try {
                sb.append(URLEncoder.encode(kv[i], "UTF-8")).append('=')
                        .append(URLEncoder.encode(kv[i + 1], "UTF-8"));
            } catch (UnsupportedEncodingException impossible) {
            }
        }
        return sb.toString();
    }

    private void writeForm(HttpURLConnection conn, String body) throws IOException {
        OutputStream out = conn.getOutputStream();
        try {
            out.write(body.getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
        conn.connect();
    }

    private String read(HttpURLConnection conn) {
        try {
            InputStream in = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) return null;
            Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A");
            String body = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            return body;
        } catch (IOException e) {
            return null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String deepLink(String token) {
        return "https://t.me/" + botUsername + "?start=" + token;
    }
}
