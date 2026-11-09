package org.skillhub.adapter.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.skillhub.config.SiteProperties;
import org.skillhub.port.LibraryPort;
import org.skillhub.support.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 从磁盘读取统一 Skill 库（{@code <工程根>/skills/catalog.json} + {@code skills/<slug>/}）。
 *
 * <p><b>为什么读文件系统而不是塞进 jar</b>：库有 57 MB / 2 523 个文件，打进 jar 会让
 * 三个工程各多出一份重复副本，且无法直观核对"三个工程的库是否一致"。
 * 放磁盘上，`tools/sync-skill-library.mjs --check` 可以对目录做指纹比对。
 *
 * <p>目录解析顺序：配置值 → 当前工作目录下的 {@code skills/} → 上一级目录下的 {@code skills/}
 * （后者覆盖"在 backend/ 里直接跑"的场景）。
 */
@Component
public class FileLibraryAdapter implements LibraryPort {

    private static final Logger log = LoggerFactory.getLogger(FileLibraryAdapter.class);
    private static final List<String> SKIP_DIRS = List.of(".git", "node_modules", "__pycache__", ".idea");

    private final ObjectMapper mapper = new ObjectMapper();
    private final SiteProperties props;

    private volatile Path resolvedRoot;
    private volatile boolean resolved;

    public FileLibraryAdapter(SiteProperties props) {
        this.props = props;
    }

    @Override
    public boolean available() {
        return resolveRoot() != null && Files.isRegularFile(root().resolve("catalog.json"));
    }

    @Override
    public String libraryVersion() {
        Path root = resolveRoot();
        if (root == null) {
            return "unknown";
        }
        try {
            JsonNode node = mapper.readTree(Files.readString(root.resolve("catalog.json"), StandardCharsets.UTF_8));
            return node.path("version").asText("unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }

    @Override
    public Path root() {
        Path r = resolveRoot();
        return r == null ? Paths.get(props.getLibrary().getDir()).toAbsolutePath() : r;
    }

    @Override
    public List<Entry> catalog() {
        Path root = resolveRoot();
        if (root == null) {
            log.warn("Skill 库不可用：找不到 skills 目录（配置值 {}），站点会以空库启动", props.getLibrary().getDir());
            return List.of();
        }
        Path catalogFile = root.resolve("catalog.json");
        if (!Files.isRegularFile(catalogFile)) {
            log.warn("Skill 库缺少 catalog.json：{}。怎么办：在工程根执行 node tools/sync-skill-library.mjs", catalogFile);
            return List.of();
        }
        try {
            JsonNode rootNode = mapper.readTree(Files.readString(catalogFile, StandardCharsets.UTF_8));
            JsonNode skills = rootNode.path("skills");
            List<Entry> out = new ArrayList<>(skills.size());
            for (JsonNode n : skills) {
                out.add(toEntry(n));
            }
            out.sort(Comparator.comparing(Entry::slug));
            return out;
        } catch (IOException e) {
            log.error("读取 catalog.json 失败：{}", catalogFile, e);
            return List.of();
        }
    }

    @Override
    public SkillContent read(String slug) {
        Path root = resolveRoot();
        if (root == null || slug == null || slug.isBlank() || slug.contains("..") || slug.contains("/")) {
            return null;
        }
        Path dir = root.resolve(slug);
        Path mdPath = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(mdPath)) {
            return null;
        }
        try {
            String content = Files.readString(mdPath, StandardCharsets.UTF_8);
            String normalized = org.skillhub.support.SkillMdParser.normalizeNewlines(content);
            List<FileRef> files = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> !isSkipped(dir, p))
                        .sorted()
                        .forEach(p -> {
                            try {
                                byte[] bytes = Files.readAllBytes(p);
                                String rel = dir.relativize(p).toString().replace('\\', '/');
                                files.add(new FileRef(rel, bytes.length, Texts.sha256(bytes)));
                            } catch (IOException ignore) {
                                // 单个文件读失败不影响整个 Skill
                            }
                        });
            }
            byte[] mdBytes = normalized.getBytes(StandardCharsets.UTF_8);
            return new SkillContent(slug, normalized, mdBytes.length, Texts.sha256(mdBytes), files);
        } catch (IOException e) {
            log.warn("读取 Skill 目录失败：{}", dir, e);
            return null;
        }
    }

    @Override
    public byte[] readFile(String slug, String relativePath) {
        Path root = resolveRoot();
        if (root == null || slug == null || slug.isBlank() || relativePath == null || relativePath.isBlank()) {
            return null;
        }
        if (slug.contains("..") || slug.contains("/") || relativePath.contains("..")) {
            return null;
        }
        Path target = root.resolve(slug).resolve(relativePath).normalize();
        if (!target.startsWith(root.resolve(slug).normalize()) || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isSkipped(Path base, Path file) {        Path rel = base.relativize(file);
        for (Path part : rel) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private Entry toEntry(JsonNode n) {
        String sourceUrl = n.path("source_url").isNull() ? null : n.path("source_url").asText(null);
        String repo = n.path("source_repo").isNull() ? null : n.path("source_repo").asText(null);
        String author = "";
        String authorKind = "department";
        if (repo != null && repo.contains("/")) {
            author = repo.substring(0, repo.indexOf('/'));
        }
        if (author.isBlank()) {
            author = n.path("origins").isArray() && n.path("origins").size() > 0
                    ? n.path("origins").get(0).asText("library") : "library";
        }
        String source = n.path("source").isNull() ? "library" : n.path("source").asText("library");
        String tags = n.path("tags").isArray()
                ? String.join(",", toStringList(n.path("tags")))
                : "";
        return new Entry(
                n.path("slug").asText(),
                n.path("name").asText(n.path("slug").asText()),
                n.path("description").asText(""),
                n.path("category").asText("general").toLowerCase(Locale.ROOT),
                n.path("scene").asText("general").toLowerCase(Locale.ROOT),
                n.path("version").asText("1.0.0"),
                n.path("license").asText("UNKNOWN"),
                source,
                sourceUrl,
                repo,
                author,
                authorKind,
                tags,
                n.path("file_count").asInt(1),
                n.path("bytes").asLong(0));
    }

    private List<String> toStringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : array) {
            String v = item.asText("").trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private Path resolveRoot() {
        if (resolved) {
            return resolvedRoot;
        }
        synchronized (this) {
            if (resolved) {
                return resolvedRoot;
            }
            Path candidate = Paths.get(props.getLibrary().getDir()).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate.resolve("skills"))) {
                candidate = candidate.resolve("skills");
            }
            if (!Files.isRegularFile(candidate.resolve("catalog.json"))) {
                Path parent = candidate.getParent();
                if (parent != null && Files.isRegularFile(parent.resolve("skills").resolve("catalog.json"))) {
                    Path sibling = parent.resolve("skills");
                    log.info("Skill 库目录回落：{} → {}", candidate, sibling);
                    candidate = sibling;
                }
            }
            resolvedRoot = Files.isDirectory(candidate) ? candidate : null;
            resolved = true;
            if (resolvedRoot != null) {
                log.info("Skill 库根目录：{}", resolvedRoot);
            }
            return resolvedRoot;
        }
    }
}
