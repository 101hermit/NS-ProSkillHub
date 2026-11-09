package org.skillhub.config;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 出网（NetEgress）：站内**所有**外部调用（Agent 探活/调用、MCP 探活/调用）共用这一条出口。
 *
 * <p>内网的两个典型症状，必须分别处理：
 * <ol>
 *   <li><b>直连外部 443</b> → {@code SSLHandshakeException: Remote host terminated the handshake}
 *       —— 企业网关在 TLS 握手阶段就把连接掐了，说明<b>没走代理</b>。</li>
 *   <li><b>走了代理但没装根 CA</b> → {@code PKIX path building failed}
 *       —— 代理做了 TLS 解密，而 JVM 不信任它的证书，说明<b>缺公司根证书</b>。</li>
 * </ol>
 *
 * <p>因此本类做两件事：
 * <b>①</b> 从配置/环境变量/私网白名单决定"走不走代理"；
 * <b>②</b> 启动时把 {@code config/certs/*.pem} 动态构建成 {@link SSLContext}
 * （不需要用户手工 {@code keytool}，也不需要 {@code -Djavax.net.ssl.trustStore}）。
 */
@Component
public class NetEgress {

    private static final Logger log = LoggerFactory.getLogger(NetEgress.class);

    private final String proxyUri;
    private final List<String> noProxyHosts;
    private final SSLContext sslContext;
    private final long connectTimeoutMs;
    private final String caBundle;

    public NetEgress(SiteProperties props, CertLocator locator) {
        this.proxyUri = resolveProxy(props.getNet().getProxy());
        this.noProxyHosts = new ArrayList<>(props.getNet().getNoProxy());
        this.connectTimeoutMs = props.getNet().getConnectTimeoutMs();
        this.caBundle = locator.resolve(props.getNet().getCaBundle());
        this.sslContext = buildSslContext(this.caBundle);
        log.info("出网策略：proxy={} caBundle={} directHosts={}",
                proxyUri == null ? "直连" : proxyUri,
                caBundle == null ? "（JDK 默认信任库）" : caBundle,
                noProxyHosts);
    }

    /** 是否走代理（null = 直连）。 */
    public String proxyUri() {
        return proxyUri;
    }

    public long connectTimeoutMs() {
        return connectTimeoutMs;
    }

    /** 新建一个带代理与信任库的 HTTP 客户端。 */
    public HttpClient newClient(Duration requestTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxyUri != null) {
            builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(
                    hostOf(proxyUri), portOf(proxyUri))));
        }
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    /**
     * 按**目标地址**决定要不要走代理，再返回客户端。
     *
     * <p>这是上一版的一个真实 bug：`newClient` 无条件套上代理，于是对
     * `http://127.0.0.1:30801/mcp` 这种"自己调自己"的请求也被送进了公司代理，
     * 代理当然到不了 localhost，表现为"接入测试总是超时"。
     *
     * <p>规则：私网 / 回环 / 白名单 → 直连；其余 → 走代理。
     */
    public HttpClient newClientFor(String url, Duration requestTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL);
        String host = hostOfUrl(url);
        if (proxyUri != null && !isDirect(host)) {
            builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(
                    hostOf(proxyUri), portOf(proxyUri))));
        }
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    /** 从完整 URL 里取 host（解析失败返回空串，等价于直连）。 */
    private static String hostOfUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            return java.net.URI.create(url.trim()).getHost();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 供页面/日志展示：这条 URL 会走代理还是直连。 */
    public String routeOf(String url) {
        String host = hostOfUrl(url);
        return isDirect(host) ? "direct" : (proxyUri == null ? "direct(no-proxy-configured)" : "proxy " + proxyUri);
    }
    /** 给定 host 是否应当直连（私网 / 白名单）。 */
    public boolean isDirect(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h) || "0.0.0.0".equals(h)) {
            return true;
        }
        if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            int second = parseIntOr(h.split("\\.").length > 1 ? h.split("\\.")[1] : "", -1);
            if (second >= 16 && second <= 31) {
                return true;
            }
        }
        for (String pattern : noProxyHosts) {
            String p = pattern.toLowerCase(Locale.ROOT).trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.startsWith("*.")) {
                if (h.endsWith(p.substring(1))) {
                    return true;
                }
            } else if (p.startsWith(".")) {
                if (h.endsWith(p) || h.equals(p.substring(1))) {
                    return true;
                }
            } else if (h.equals(p) || h.endsWith("." + p)) {
                return true;
            }
        }
        return false;
    }

    /** 供 {@code /api/v1/health} 展示的出网自述。 */
    public Map<String, Object> describe() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("proxy", proxyUri == null ? "none" : proxyUri);
        out.put("proxy_source", proxyUri == null ? "direct" : "config/env");
        out.put("ca_bundle", caBundle == null ? "" : caBundle);
        out.put("ca_loaded", sslContext != null);
        out.put("connect_timeout_ms", connectTimeoutMs);
        out.put("no_proxy", noProxyHosts);
        return out;
    }

    // ------------------------------------------------------------------ 内部

    private static String resolveProxy(String configured) {
        if (configured != null && !configured.isBlank()) {
            return "none".equalsIgnoreCase(configured.trim()) ? null : configured.trim();
        }
        for (String key : List.of("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy", "ALL_PROXY")) {
            String v = System.getenv(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /** 把 PEM/DER 文件（或目录、或分号分隔的多个路径）构建成 SSLContext。 */
    private static SSLContext buildSslContext(String bundle) {
        if (bundle == null || bundle.isBlank()) {
            return null;
        }
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            int count = 0;
            for (String part : bundle.split(";")) {
                Path path = Paths.get(part.trim());
                List<Path> files = new ArrayList<>();
                if (Files.isDirectory(path)) {
                    try (var stream = Files.list(path)) {
                        stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).matches(".*\\.(pem|crt|cer|der)$"))
                                .forEach(files::add);
                    }
                } else if (Files.isRegularFile(path)) {
                    files.add(path);
                }
                for (Path f : files) {
                    try (InputStream in = Files.newInputStream(f)) {
                        for (Certificate cert : cf.generateCertificates(in)) {
                            ks.setCertificateEntry("ca-" + (count++), cert);
                        }
                    }
                }
            }
            if (count == 0) {
                return null;
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            log.info("已加载 {} 张自定义根证书", count);
            return ctx;
        } catch (Exception e) {
            log.warn("加载根证书失败（将回退到 JDK 默认信任库）：{}", e.getMessage());
            return null;
        }
    }

    private static String hostOf(String uri) {
        String s = uri.replaceFirst("^[a-zA-Z]+://", "");
        int at = s.indexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int colon = s.lastIndexOf(':');
        return colon > 0 ? s.substring(0, colon) : s;
    }

    private static int portOf(String uri) {
        String s = uri.replaceFirst("^[a-zA-Z]+://", "");
        int at = s.indexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int colon = s.lastIndexOf(':');
        return colon > 0 ? parseIntOr(s.substring(colon + 1), 8080) : 8080;
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 基础认证头（代理需要时用；当前内网代理不需要，保留以便换环境）。 */
    public String basicAuth(String user, String pass) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
