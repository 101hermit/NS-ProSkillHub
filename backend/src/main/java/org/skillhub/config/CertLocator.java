package org.skillhub.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 找公司根证书该从哪里加载。
 *
 * <p>解析顺序（命中即停）：
 * <ol>
 *   <li>配置 {@code skillhub.net.ca-bundle}（可分号分隔多个路径）</li>
 *   <li>环境变量 {@code SKHUB_CA_BUNDLE} / {@code SSL_CERT_FILE} / {@code REQUESTS_CA_BUNDLE}</li>
 *   <li>相对工程根的 {@code config/certs/}、{@code ../config/certs/}（仓库级共享证书目录）</li>
 * </ol>
 *
 * <p>为什么不猜系统证书库：Java 的 {@code cacerts} 只认 JDK 自带 CA，
 * 而企业代理的根证书不在其中。让用户跑一次脚本导出到 {@code config/certs/} 更可靠。
 */
@Component
public class CertLocator {

    private static final Logger log = LoggerFactory.getLogger(CertLocator.class);

    public String resolve(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        for (String key : List.of("SKHUB_CA_BUNDLE", "SSL_CERT_FILE", "REQUESTS_CA_BUNDLE", "NODE_EXTRA_CA_CERTS")) {
            String v = System.getenv(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("config", "certs"));
        candidates.add(Paths.get("..", "config", "certs"));
        candidates.add(Paths.get("..", "..", "config", "certs"));
        for (Path p : candidates) {
            Path abs = p.toAbsolutePath().normalize();
            if (Files.isDirectory(abs) && hasCert(abs)) {
                log.info("使用证书目录：{}", abs);
                return abs.toString();
            }
        }
        return null;
    }

    private static boolean hasCert(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.toString().toLowerCase(java.util.Locale.ROOT)
                    .matches(".*\\.(pem|crt|cer|der)$"));
        } catch (Exception e) {
            return false;
        }
    }
}
