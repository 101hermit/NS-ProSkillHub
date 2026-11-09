package org.skillhub.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 站点配置（{@code skillhub.*}）。三个工程共用同一组键，值不同。
 */
@ConfigurationProperties(prefix = "skillhub")
public class SiteProperties {

    /** 产品标识：ESKH / SSKH / PSKH。 */
    private String product = "ESKH";
    /** 产品全名。 */
    private String productName = "EasySkillHub";
    /** 产品缩写。 */
    private String abbr = "ESKH";
    /** 版本号。 */
    private String version = "1.0";
    /** 站点名称（页面标题）。 */
    private String siteName = "EasySkillHub · Skill 资源站";
    /** 一句话定位。 */
    private String tagline = "资源站模式：Skill 的建立、管理、分发、评估";
    /** 端口（只用于前端展示与文档对齐）。 */
    private int port = 30800;
    /** 版本强调色（前端令牌）。 */
    private String accent = "#0f9d58";

    private final Library library = new Library();
    private final Upload upload = new Upload();
    private final Eval eval = new Eval();

    /** Skill 库位置。 */
    public static class Library {
        /** 库根目录（含 catalog.json 与各 Skill 子目录）。 */
        private String dir = "./skills";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }

    /** 上传落盘位置。 */
    public static class Upload {
        private String dir = "./data/uploads";
        /** 单个附件包上限（字节）。 */
        private long maxBundleBytes = 32L * 1024 * 1024;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getMaxBundleBytes() {
            return maxBundleBytes;
        }

        public void setMaxBundleBytes(long maxBundleBytes) {
            this.maxBundleBytes = maxBundleBytes;
        }
    }

    /** 评估相关（标准版/专业版使用，资源站保留占位）。 */
    public static class Eval {
        /** 是否启用在线实测（资源站恒为 false）。 */
        private boolean liveEnabled = false;
        /** 单次实测总超时（毫秒）。 */
        private long timeoutMs = 90_000;

        public boolean isLiveEnabled() {
            return liveEnabled;
        }

        public void setLiveEnabled(boolean liveEnabled) {
            this.liveEnabled = liveEnabled;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    private final Net net = new Net();

    /** 出网配置（标准版/专业版才有；资源站恒为直连且不发起任何外部调用）。 */
    public static class Net {
        /** 代理地址；留空则读 HTTPS_PROXY/HTTP_PROXY/ALL_PROXY；填 none 强制直连。 */
        private String proxy = "";
        /** 不走代理的 host（支持 *.suffix 与 .suffix）。私网地址代码里已强制直连。 */
        private List<String> noProxy = new java.util.ArrayList<>(List.of(
                "localhost", "127.0.0.1", "::1", ".neusoft.com", ".intra", ".corp"));
        /** 公司根证书文件/目录（分号分隔多个）。 */
        private String caBundle = "";
        /** 连接超时（毫秒）。外部模型首字慢是正常，连接建不起来才是真问题。 */
        private long connectTimeoutMs = 8000;

        public String getProxy() {
            return proxy;
        }

        public void setProxy(String proxy) {
            this.proxy = proxy;
        }

        public List<String> getNoProxy() {
            return noProxy;
        }

        public void setNoProxy(List<String> noProxy) {
            this.noProxy = noProxy;
        }

        public String getCaBundle() {
            return caBundle;
        }

        public void setCaBundle(String caBundle) {
            this.caBundle = caBundle;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }
    }

    /** 本版本提供的能力（前端据此渲染菜单，见 docs/03 capabilities）。 */

    public List<String> capabilities() {
        return List.of("skill.catalog", "skill.edit", "skill.contribute", "skill.distribute", "risk.scan");
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getAbbr() {
        return abbr;
    }

    public void setAbbr(String abbr) {
        this.abbr = abbr;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAccent() {
        return accent;
    }

    public void setAccent(String accent) {
        this.accent = accent;
    }

    public Library getLibrary() {
        return library;
    }

    public Upload getUpload() {
        return upload;
    }

    public Eval getEval() {
        return eval;
    }

    public Net getNet() {
        return net;
    }
}
