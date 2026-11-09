package org.skillhub.adapter.packaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.skillhub.port.PackagePort;
import org.springframework.stereotype.Component;

/**
 * ZIP 打包实现（{@code java.util.zip}，零依赖）。
 *
 * <p>归档结构固定为 {@code <entryName>/<path>}，下载后解压即可直接放进
 * {@code ~/.claude/skills}，这是 Skill 安装的既成约定。
 */
@Component
public class ZipPackageAdapter implements PackagePort {

    private static final long MAX_ENTRIES = 5_000;

    @Override
    public byte[] zip(String entryName, List<FileContent> files) {
        String base = sanitize(entryName);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
        long count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (FileContent f : files) {
                if (++count > MAX_ENTRIES) {
                    break;
                }
                String path = base + "/" + sanitizeRelative(f.path());
                ZipEntry entry = new ZipEntry(path);
                entry.setTime(System.currentTimeMillis());
                zos.putNextEntry(entry);
                zos.write(f.data());
                zos.closeEntry();
            }
            zos.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("打包失败", e);
        }
        return bos.toByteArray();
    }

    private static String sanitize(String name) {
        String s = name == null ? "skill" : name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return s.isEmpty() ? "skill" : s;
    }

    /** 防 zip-slip：删除 {@code ..} 段与绝对路径。 */
    private static String sanitizeRelative(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        StringBuilder sb = new StringBuilder();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || ".".equals(seg) || "..".equals(seg)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(seg);
        }
        return sb.length() == 0 ? "unnamed.bin" : sb.toString();
    }
}
