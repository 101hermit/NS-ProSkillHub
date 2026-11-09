package org.skillhub.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.skillhub.mapper.DocMapper;
import org.skillhub.model.DocPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把 {@code classpath:docs/*.md}（站点指南）灌进 {@code doc_page} 表。
 *
 * <p>这样"在线文档"和"页面"用同一套数据源：改文档只改 Markdown，重启即生效，
 * 不需要改代码，也不需要人工往库里插数据。
 */
@Service
public class DocSeedService {

    private static final Logger log = LoggerFactory.getLogger(DocSeedService.class);
    private static final String MANIFEST = "docs/manifest.json";

    private final DocMapper docMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public DocSeedService(DocMapper docMapper) {
        this.docMapper = docMapper;
    }

    /** 导入结果：写入/更新的文档数。 */
    public record Report(int total, int written) {
    }

    @Transactional
    public Report seed() {
        JsonNode manifest = readManifest();
        if (manifest == null || !manifest.isArray()) {
            log.warn("找不到或无法解析 {}，跳过在线文档初始化", MANIFEST);
            return new Report(0, 0);
        }
        int written = 0;
        for (JsonNode node : manifest) {
            String key = node.path("doc_key").asText("");
            String file = node.path("file").asText("");
            if (key.isEmpty() || file.isEmpty()) {
                continue;
            }
            String body = readClasspath("docs/" + file);
            if (body == null) {
                log.warn("文档文件缺失：docs/{}", file);
                continue;
            }
            DocPage page = new DocPage();
            page.docKey = key;
            page.title = node.path("title").asText(key);
            page.section = node.path("section").asText("guide");
            page.summary = node.path("summary").asText("");
            page.body = body;
            page.sortNo = node.path("sort_no").asInt(100);
            if (docMapper.findByKey(key) == null) {
                docMapper.insert(page);
            } else {
                docMapper.update(page);
            }
            written++;
        }
        log.info("在线文档已同步：{}/{} 篇", written, manifest.size());
        return new Report(manifest.size(), written);
    }

    private JsonNode readManifest() {
        String raw = readClasspath(MANIFEST);
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (IOException e) {
            log.error("解析 {} 失败", MANIFEST, e);
            return null;
        }
    }

    private static String readClasspath(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
