package org.skillhub.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本与哈希工具（纯函数，无状态，可直接单测）。
 */
public final class Texts {

    /** 源码直链的三种合法平台（doc 要求：GitHub / Gitee / GitLab）。 */
    private static final Pattern REPO_URL = Pattern.compile(
            "^https?://(github\\.com|gitee\\.com|gitlab\\.com|gitcode\\.com)/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$",
            Pattern.CASE_INSENSITIVE);

    private Texts() {
    }

    public static String sha256(String s) {
        return sha256((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    /** 归一化成 slug：小写、非字母数字转 {@code -}、去首尾、截断 80 字符。 */
    public static String slugify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "skill";
        }
        String s = raw.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            return "skill";
        }
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /**
     * 解析源码直链，得出 Skill 身份：{@code <host>:<owner>/<repo>/<name>}。
     *
     * <p>只接受 GitHub / Gitee / GitLab / GitCode 四种格式；其它一律返回空 Map，
     * 由调用方回落到 {@code local:<contributor>/<name>}。
     */
    public static Map<String, String> parseRepoUrl(String url, String skillName) {
        Map<String, String> out = new LinkedHashMap<>();
        if (url == null || url.isBlank()) {
            return out;
        }
        Matcher m = REPO_URL.matcher(url.trim());
        if (!m.matches()) {
            return out;
        }
        String host = switch (m.group(1).toLowerCase(Locale.ROOT)) {
            case "github.com" -> "github";
            case "gitee.com" -> "gitee";
            case "gitlab.com" -> "gitlab";
            default -> "gitcode";
        };
        String owner = m.group(2);
        String repo = m.group(3).replaceAll("\\.git$", "");
        String name = slugify(firstNonBlank(skillName, repo));
        out.put("host", host);
        out.put("owner", owner);
        out.put("repo", repo);
        out.put("name", name);
        out.put("source", host);
        out.put("skill_id", host + ":" + owner + "/" + repo + "/" + name);
        out.put("source_repo", owner + "/" + repo);
        return out;
    }

    /** 人类可读字节数（页面展示用）。 */
    public static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
    }

    /** 把 {@code a,b,c} 形式的标签串解析成列表。 */
    public static java.util.List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static String joinTags(java.util.Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream().map(String::trim).filter(s -> !s.isEmpty())
                .distinct().limit(20)
                .reduce((a, b) -> a + "," + b).orElse("");
    }
}
