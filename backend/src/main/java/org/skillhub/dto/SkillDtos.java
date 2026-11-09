package org.skillhub.dto;

import java.util.List;
import java.util.Map;

/** Skill 相关的对外形状。 */
public final class SkillDtos {

    private SkillDtos() {
    }

    /**
     * 列表卡片（不含正文）。字段集合在三系统保持一致（docs/03 §2）。
     */
    public record Card(
            String skillId,
            String slug,
            String name,
            String title,
            String description,
            String categoryCode,
            String categoryLabel,
            String sceneCode,
            String sceneLabel,
            List<String> tags,
            String version,
            String source,
            String sourceUrl,
            String sourceRepo,
            String license,
            String author,
            String authorKind,
            String authorKindLabel,
            int riskScore,
            String riskLevel,
            String riskLabel,
            String status,
            int downloadCount,
            int installCount,
            int starCount,
            int fileCount,
            long packageBytes,
            int contentBytes,
            int evalCount,
            double avgScore,
            String createdAt,
            String updatedAt) {
    }

    /** 详情页：卡片 + 正文 + 附件 + 评估 + 安装方式 + 版本 + 相关技能。 */
    public record Detail(
            Card card,
            String content,
            List<FileItem> files,
            Audit audit,
            List<InstallMethod> install,
            List<Version> versions,
            List<Card> related) {
    }

    /** 附件条目。 */
    public record FileItem(String path, long bytes, boolean primary) {
    }

    /** 版本（列表里不带正文）。 */
    public record Version(String version, String notes, int sizeBytes, String origin, String createdAt) {
    }

    /** 评估报告。 */
    public record Audit(int riskScore, String riskLevel, String riskLabel, String engine,
                        List<DimensionScore> dimensions, Map<String, Integer> severityCounts,
                        int signalTotal, String summary, String conclusion, String createdAt,
                        List<Signal> signals) {
    }

    /** 维度分布（风险分贡献）。 */
    public record DimensionScore(String key, String name, int score, int signalCount) {
    }

    /** 单条风险信号。 */
    public record Signal(String code, String indicator, String dimension, String dimensionName,
                         String severity, String severityLabel, int weight,
                         String filePath, int lineNo, String remediation) {
    }

    /** 安装/分发方式。 */
    public record InstallMethod(String type, String title, String description, String value, String language) {
    }

    /** 编辑请求（null 表示不改）。字段校验在动作层用 @Valid 完成。 */
    public static class EditRequest {
        private String name;
        private String title;
        private String description;
        private String categoryCode;
        private String sceneCode;
        private List<String> tags;
        private String version;
        private String sourceUrl;
        private String license;
        private String content;
        private String versionNote;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(String categoryCode) {
            this.categoryCode = categoryCode;
        }

        public String getSceneCode() {
            return sceneCode;
        }

        public void setSceneCode(String sceneCode) {
            this.sceneCode = sceneCode;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getLicense() {
            return license;
        }

        public void setLicense(String license) {
            this.license = license;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getVersionNote() {
            return versionNote;
        }

        public void setVersionNote(String versionNote) {
            this.versionNote = versionNote;
        }
    }
}
