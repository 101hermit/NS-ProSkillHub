package org.skillhub.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 从用户上传的 ZIP 里抽出"可扫描的文本"，供风险引擎一并评估。
 *
 * <p>只读文本类扩展名，单文件 ≤ 256 KB，合计 ≤ 512 KB —— 既够覆盖脚本与配置，
 * 又不会被一个 30 MB 的二进制包拖死。解压前先看条目名，避免 zip-slip。
 */
public final class BundleTextReader {

    private static final List<String> TEXT_EXT = List.of(
            ".md", ".txt", ".py", ".sh", ".bash", ".js", ".mjs", ".cjs", ".ts", ".json", ".yaml", ".yml",
            ".toml", ".ini", ".cfg", ".conf", ".java", ".kt", ".go", ".rs", ".rb", ".php", ".sql",
            ".html", ".htm", ".css", ".xml", ".ps1", ".bat", ".cmd", ".dockerfile", "");

    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_TOTAL_BYTES = 512 * 1024;

    private BundleTextReader() {
    }

    /** 抽取结果：{@code text} 为拼好的正文，{@code fileCount} 为参与扫描的文件数。 */
    public record Result(String text, int fileCount) {
    }

    public static Result extract(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            return new Result("", 0);
        }
        StringBuilder sb = new StringBuilder();
        int files = 0;
        int total = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || isUnsafe(entry.getName()) || !isTextual(entry.getName())) {
                    continue;
                }
                int read;
                int fileBytes = 0;
                StringBuilder fileContent = new StringBuilder();
                while ((read = zis.read(buf)) > 0) {
                    if (fileBytes + read > MAX_FILE_BYTES) {
                        break;
                    }
                    fileBytes += read;
                    fileContent.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                }
                if (total + fileBytes > MAX_TOTAL_BYTES) {
                    break;
                }
                total += fileBytes;
                files++;
                sb.append("\n\n### ").append(entry.getName()).append('\n').append(fileContent);
            }
        } catch (IOException e) {
            // 损坏的 zip 不应该让整个贡献流程失败：返回已抽到的部分
            return new Result(sb.toString(), files);
        }
        return new Result(sb.toString(), files);
    }

    private static boolean isUnsafe(String name) {
        return name == null || name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                || name.contains(":");
    }

    private static boolean isTextual(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String ext = dot < 0 ? "" : lower.substring(dot);
        return TEXT_EXT.contains(ext);
    }
}
