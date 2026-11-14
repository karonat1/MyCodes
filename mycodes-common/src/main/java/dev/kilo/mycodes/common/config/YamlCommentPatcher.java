package dev.kilo.mycodes.common.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Патчер YAML-файлов, сохраняющий комментарии: добавляет блоки ключей
 * (вместе с комментариями и вложенными секциями) из обновлённого дефолтного
 * конфига в пользовательский файл в правильное место структуры.
 */
public final class YamlCommentPatcher {

    public static class Block {
        public final String path;
        public final int indent;
        public final boolean section;
        public final int start;   // первая строка блока (включая комментарии)
        public final int body;    // строка с ключом
        public int subtreeEnd;    // последняя строка поддерева

        Block(String path, int indent, boolean section, int start, int body) {
            this.path = path;
            this.indent = indent;
            this.section = section;
            this.start = start;
            this.body = body;
            this.subtreeEnd = body;
        }
    }

    private YamlCommentPatcher() {
    }

    /** Разбирает YAML-текст в упорядоченные блоки с их строчными диапазонами */
    public static List<Block> parse(List<String> lines) {
        List<Block> blocks = new ArrayList<Block>();
        Deque<String> sectionPath = new ArrayDeque<String>();
        Deque<Integer> sectionIndent = new ArrayDeque<Integer>();
        int commentStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (commentStart < 0) commentStart = i;
                continue;
            }
            if (trimmed.startsWith("-")) {
                // элемент списка — продолжение предыдущего блока
                if (!blocks.isEmpty()) {
                    Block last = blocks.get(blocks.size() - 1);
                    last.subtreeEnd = Math.max(last.subtreeEnd, i);
                }
                continue;
            }
            int indent = indent(raw);
            while (!sectionIndent.isEmpty() && sectionIndent.peek() >= indent) {
                sectionIndent.pop();
                sectionPath.pop();
            }
            int colon = trimmed.indexOf(':');
            String name = colon < 0 ? trimmed : trimmed.substring(0, colon);
            String value = colon < 0 ? "" : trimmed.substring(colon + 1).trim();
            String parent = sectionPath.isEmpty() ? "" : join(sectionPath);
            String path = parent.isEmpty() ? name : parent + "." + name;
            boolean isSection = value.isEmpty();
            int start = commentStart >= 0 ? commentStart : i;
            commentStart = -1;
            blocks.add(new Block(path, indent, isSection, start, i));
            if (isSection) {
                sectionIndent.push(indent);
                sectionPath.push(name);
            }
        }
        // поддеревья
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block b = blocks.get(i);
            int e = b.subtreeEnd;
            if (i + 1 < blocks.size() && blocks.get(i + 1).indent > b.indent) {
                e = Math.max(e, blocks.get(i + 1).subtreeEnd);
            }
            blocks.get(i).subtreeEnd = e;
        }
        return blocks;
    }

    private static int indent(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
    }

    private static String join(Deque<String> stack) {
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0) sb.append('.');
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Возвращает новый текст пользовательского файла со вставленными
     * недостающими блоками (с комментариями) или null, если вставок нет.
     */
    public static String patch(String userText, String defaultText) {
        List<String> userLines = splitLines(userText);
        List<String> defLines = splitLines(defaultText);
        Set<String> defPaths = new LinkedHashSet<String>();
        for (Block b : parse(defLines)) defPaths.add(b.path);
        Map<String, Block> userBlocks = new LinkedHashMap<String, Block>();
        for (Block b : parse(userLines)) {
            userBlocks.put(b.path, b);
        }
        // недостающие блоки, у которых нет недостающего родителя (родитель вставится целиком)
        List<Block> missingTop = new ArrayList<Block>();
        Set<String> missingAny = new LinkedHashSet<String>();
        Map<String, Block> defMap = new LinkedHashMap<String, Block>();
        List<Block> defBlocks = parse(defLines);
        for (Block b : defBlocks) defMap.put(b.path, b);
        for (Block b : defBlocks) {
            if (!userBlocks.containsKey(b.path)) missingAny.add(b.path);
        }
        for (String missing : missingAny) {
            boolean covered = false;
            // проверим, нет ли недостающего родителя — если есть, то вставится весь его блок
            String parent = defMap.get(missing) != null ? parentOf(missing) : "";
            while (!parent.isEmpty()) {
                if (missingAny.contains(parent)) {
                    covered = true;
                    break;
                }
                parent = parentOf(parent);
            }
            if (!covered) missingTop.add(defMap.get(missing));
        }
        if (missingTop.isEmpty()) return null;

        // вставки: якорь — конец контента ближайшего существующего предка
        Map<Integer, List<String>> insertions = new java.util.TreeMap<Integer, List<String>>();
        for (Block m : missingTop) {
            int index;
            String parent = parentOf(m.path);
            Block anchor = null;
            while (!parent.isEmpty()) {
                Block candidate = userBlocks.get(parent);
                if (candidate != null) {
                    anchor = candidate;
                    break;
                }
                parent = parentOf(parent);
            }
            index = anchor != null ? anchor.subtreeEnd + 1 : userLines.size();
            List<String> chunk = new ArrayList<String>();
            if (index >= userLines.size()) chunk.add(""); // отделяем от конца файла
            for (int i = m.start; i <= m.subtreeEnd; i++) {
                chunk.add(defLines.get(i));
            }
            List<String> at = insertions.get(index);
            if (at == null) {
                at = new ArrayList<String>();
                insertions.put(index, at);
            }
            at.addAll(chunk);
        }
        // вставляем с конца, чтобы индексы не смещались
        List<Integer> indexes = new ArrayList<Integer>(insertions.keySet());
        java.util.Collections.reverse(indexes);
        for (int idx : indexes) {
            userLines.addAll(Math.min(idx, userLines.size()), insertions.get(idx));
        }
        StringBuilder out = new StringBuilder();
        for (String line : userLines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String parentOf(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? "" : path.substring(0, i);
    }

    /**
     * Заменяет значение существующего ключа в тексте YAML, сохраняя комментарии
     * и форматирование. Возвращает новый текст или null, если путь не найден
     * (тогда вызывающий код может записать значение через Configuration API,
     * потеряв комментарии — деградация допустима).
     */
    public static String setValue(String yamlText, String path, String renderedValue) {
        List<String> lines = splitLines(yamlText);
        List<Block> blocks = parse(lines);
        for (Block b : blocks) {
            if (b.path.equals(path)) {
                String line = lines.get(b.body);
                int indent = indent(line);
                int comment = 0;
                while (comment < line.length() && line.charAt(comment) != '#') comment++;
                String inline = comment > indent ? line.substring(comment) : "";
                String rebuilt = spaces(indent) + lastSegment(path) + ": " + renderedValue + (inline.isEmpty() ? "" : " " + inline.trim());
                lines.set(b.body, rebuilt);
                StringBuilder out = new StringBuilder();
                for (String l : lines) {
                    out.append(l).append('\n');
                }
                return out.toString();
            }
        }
        return null;
    }

    /** Однострочное представление значения для YAML (строки — в кавычках) */
    public static String renderScalar(String value) {
        if (value == null) return "''";
        String v = value.replace("'", "''").replace("\\n", " ").replace("\n", " ").replace("\r", "");
        return "'" + v + "'";
    }

    public static String renderScalar(int value) {
        return String.valueOf(value);
    }

    public static String renderScalar(boolean value) {
        return String.valueOf(value);
    }

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private static String lastSegment(String path) {
        int i = path.lastIndexOf('.');
        String s = i < 0 ? path : path.substring(i + 1);
        return s.trim();
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<String>();
        int from = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                String line = text.substring(from, i);
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                lines.add(line);
                from = i + 1;
            }
        }
        if (from < text.length()) lines.add(text.substring(from));
        return lines;
    }
}
