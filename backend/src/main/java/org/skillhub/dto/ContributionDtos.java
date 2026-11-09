package org.skillhub.dto;

import java.util.List;

/** 在线贡献（生成 → 签协议 → 提交 → 发布）的形状。 */
public final class ContributionDtos {

    private ContributionDtos() {
    }

    /** 贡献记录项。 */
    public record Item(
            long id,
            String kind,
            String kindLabel,
            String contributorName,
            String name,
            String title,
            String version,
            String status,
            String statusLabel,
            boolean claSigned,
            String claSignature,
            String claSignedAt,
            String skillId,
            int riskScore,
            String riskLevel,
            String riskLabel,
            String sourceType,
            String sourceUrl,
            String notes,
            String createdAt,
            String updatedAt,
            int skillMdBytes) {
    }

    /** 贡献详情：记录 + 预评估 + 下一步提示。 */
    public record Detail(Item item, SkillDtos.Audit audit, String nextStep, List<String> checklist) {
    }

    /** 分页信封。 */
    public record Page(int page, int size, long total, int totalPages, List<Item> items) {
    }

    /** 第一步上传的请求（multipart 字段在动作层装配成这个对象）。 */
    public static class CreateRequest {
        private String kind = "department";
        private String contributorName;
        private String name;
        private String title;
        private String version = "1.0.0";
        private String description;
        private String sourceUrl;
        private String codeDraft;
        private String existingSkillId;
        private boolean autoPublish;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getContributorName() {
            return contributorName;
        }

        public void setContributorName(String contributorName) {
            this.contributorName = contributorName;
        }

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

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getCodeDraft() {
            return codeDraft;
        }

        public void setCodeDraft(String codeDraft) {
            this.codeDraft = codeDraft;
        }

        public String getExistingSkillId() {
            return existingSkillId;
        }

        public void setExistingSkillId(String existingSkillId) {
            this.existingSkillId = existingSkillId;
        }

        public boolean isAutoPublish() {
            return autoPublish;
        }

        public void setAutoPublish(boolean autoPublish) {
            this.autoPublish = autoPublish;
        }
    }

    /** 签 CLA 的请求体。 */
    public record ClaRequest(String signature) {
    }
}
