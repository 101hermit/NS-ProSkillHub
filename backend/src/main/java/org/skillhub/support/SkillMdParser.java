package org.skillhub.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SKILL.md 解析：分离 YAML frontmatter 与正文。
 *
 * <p>只支持"一层 key: value"的极简 YAML —— 这是 Skill 规范实际使用的子集。
 * 复杂嵌套（如 upstream.repository）刻意不支持：遇到就原样跳过，绝不抛异常，
 * 因为一份写坏的 SKILL.md 不应该让整个库导入失败。
 */
public final class SkillMdParser {

    private SkillMdParser() {
    }

    /** 解析结果：{@code front} 为小写 key 的字段表，{@code body} 为正文。 */
    public record Parsed(Map<String, String> front, String body) {

        public String get(String key) {
            return front.getOrDefault(key, "");
        }

        public boolean has(String key) {
            return front.containsKey(key) && !front.get(key).isBlank();
        }
    }

    public static Parsed parse(String raw) {
        String text = normalizeNewlines(raw);
        if (!text.startsWith("---")) {
            return new Parsed(Map.of(), text);
        }
        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) {
            return new Parsed(Map.of(), text);
        }
        int end = text.indexOf("\n---", firstBreak);
        if (end < 0) {
            return new Parsed(Map.of(), text);
        }
        String head = text.substring(firstBreak + 1, end);
        int bodyStart = text.indexOf('\n', end + 1);
        String body = bodyStart < 0 ? "" : text.substring(bodyStart + 1);
        return new Parsed(parseHead(head), normalizeNewlines(body).trim());
    }

    /** 解析 frontmatter 段落（不含 --- 分隔行）。 */
    public static Map<String, String> parseHead(String head) {
        Map<String, String> out = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder buf = new StringBuilder();
        for (String line : normalizeNewlines(head).split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            boolean isTop = colon > 0 && !Character.isWhitespace(line.charAt(0)) && colon < 64;
            if (isTop) {
                flush(out, currentKey, buf);
                currentKey = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                buf = new StringBuilder(stripQuotes(line.substring(colon + 1).trim()));
            } else if (currentKey != null) {
                // 续行（折行的描述或缩进子键）：拼进当前值，保证 description 不丢
                String cont = line.trim();
                if (buf.length() > 0 && !cont.isEmpty()) {
                    buf.append(' ');
                }
                buf.append(cont);
            }
        }
        flush(out, currentKey, buf);
        return out;
    }

    private static void flush(Map<String, String> out, String key, StringBuilder buf) {
        if (key == null || key.isEmpty()) {
            return;
        }
        String value = buf.toString().trim();
        if (value.equals("|") || value.equals(">") || value.equals("|-") || value.equals(">-")) {
            value = "";
        }
        out.put(key, stripQuotes(value));
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    public static String normalizeNewlines(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", "\n").replace('\r', '\n');
        return t.startsWith("\uFEFF") ? t.substring(1) : t;
    }

    /** 从 frontmatter 中挑出可用的标签。 */
    public static List<String> tags(Parsed parsed) {
        String raw = parsed.has("tags") ? parsed.get("tags") : parsed.get("keywords");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
        return java.util.Arrays.stream(cleaned.split("[,;、\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(12)
                .toList();
    }
}
