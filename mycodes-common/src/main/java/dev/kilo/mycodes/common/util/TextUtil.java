package dev.kilo.mycodes.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Работа с текстом: красивые градиенты, hex-цвета и legacy-коды.
 *
 * <p>Поддерживаемая разметка:
 * <ul>
 *   <li>{@code <gradient:#RRGGBB:#RRGGBB>текст</gradient>} — плавный градиент на каждый символ;</li>
 *   <li>{@code &a..&f, &l, &m, &n, &o, &r} — стандартные Minecraft-коды;</li>
 *   <li>{@code #RRGGBB} в начале слова не поддерживается — используйте градиент.</li>
 * </ul>
 *
 * <p>Результат конвертируется в legacy-строку с §x-hex (клиенты 1.16+).
 */
public final class TextUtil {

    private static final Pattern GRADIENT =
            Pattern.compile("<gradient:#([0-9a-fA-F]{6})\\s*:\\s*#([0-9a-fA-F]{6})>(.*?)</gradient>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final char SECTION = '\u00A7';

    private TextUtil() {
    }

    /** Полная обработка разметки: градиенты + &-коды → legacy-строка */
    public static String toLegacy(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher m = GRADIENT.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rendered = gradient(m.group(1), m.group(2), ampToSection(m.group(3)));
            m.appendReplacement(sb, Matcher.quoteReplacement(rendered));
        }
        m.appendTail(sb);
        return ampToSection(sb.toString());
    }

    /** Рисует градиент по-символьно. Вход должен быть без &-кодов (они уже §). */
    public static String gradient(String fromHex, String toHex, String inner) {
        if (inner == null || inner.isEmpty()) return "";
        int[] from = rgb(fromHex);
        int[] to = rgb(toHex);
        // индексы видимых символов (без §-кодов)
        List<Integer> visible = new ArrayList<Integer>();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == SECTION && i + 1 < inner.length()) {
                i++; // skip the code — it does not participate in the gradient
            } else {
                visible.add(i);
            }
        }
        int n = visible.size();
        if (n == 0) return "";
        StringBuilder out = new StringBuilder();
        int vi = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == SECTION && i + 1 < inner.length()) {
                out.append(c).append(inner.charAt(i + 1));
                i++;
                continue;
            }
            double t = n == 1 ? 0.0 : (double) vi / (double) (n - 1);
            out.append(hexLegacy(lerp(from, to, t))).append(c);
            vi++;
        }
        return out.toString();
    }

    private static int[] lerp(int[] a, int[] b, double t) {
        return new int[]{
                (int) Math.round(a[0] + (b[0] - a[0]) * t),
                (int) Math.round(a[1] + (b[1] - a[1]) * t),
                (int) Math.round(a[2] + (b[2] - a[2]) * t)
        };
    }

    /** #RRGGBB → legacy hex (§x§R§R§G§G§B§B) */
    public static String hexLegacy(int[] rgb) {
        StringBuilder sb = new StringBuilder("§x");
        appendNibble(sb, rgb[0] >> 4);
        appendNibble(sb, rgb[0] & 0xF);
        appendNibble(sb, rgb[1] >> 4);
        appendNibble(sb, rgb[1] & 0xF);
        appendNibble(sb, rgb[2] >> 4);
        appendNibble(sb, rgb[2] & 0xF);
        return sb.toString();
    }

    private static void appendNibble(StringBuilder sb, int v) {
        sb.append(SECTION).append("0123456789abcdef".charAt(v));
    }

    private static int[] rgb(String hex) {
        try {
            int v = Integer.parseInt(hex, 16);
            return new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
        } catch (NumberFormatException e) {
            return new int[]{255, 255, 255};
        }
    }

    /** &-коды → §-коды */
    public static String ampToSection(String s) {
        if (s == null) return "";
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) >= 0) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
                i++;
            }
        }
        return new String(chars);
    }

    /** Чистый текст: без тегов, цветов и legacy-кодов (для Telegram/консоли) */
    public static String strip(String input) {
        if (input == null) return "";
        String s = GRADIENT.matcher(input).replaceAll("$3");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' || c == SECTION) {
                i++; // skip the color code
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Обрезает legacy-строку до limit видимых символов
     * (учитывая, что §x-hex занимает 14 служебных символов).
     */
    public static String truncateVisible(String legacy, int limit) {
        if (legacy == null) return "";
        int visible = 0;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == SECTION) {
                out.append(c).append(legacy.charAt(i + 1));
                i++;
                continue;
            }
            if (visible >= limit) break;
            out.append(c);
            visible++;
        }
        return out.toString();
    }
}
