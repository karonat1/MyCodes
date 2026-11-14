package dev.kilo.mycodes.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Утилиты JSON на базе Gson (при сборке релоцируется в dev.kilo.mycodes.lib.gson). */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private JsonUtil() {
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static String toPretty(Object o) {
        return PRETTY.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) return null;
        try {
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject parseObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static com.google.gson.JsonElement parseElement(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static com.google.gson.JsonArray parseArray(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonArray();
        } catch (Exception e) {
            return new com.google.gson.JsonArray();
        }
    }
}
