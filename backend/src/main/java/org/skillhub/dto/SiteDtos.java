package org.skillhub.dto;

import java.util.List;
import java.util.Map;

/** 站点级形状：元信息、能力、健康、统计、分类、文档、贡献者、审计流水。 */
public final class SiteDtos {

    private SiteDtos() {
    }

    /** {@code GET /api/v1/meta}。字段集合三系统一致（值不同）。 */
    public record Meta(
            String product,
            String productName,
            String abbr,
            String version,
            String siteName,
            String tagline,
            int port,
            String accent,
            String mode,
            List<Capability> features,
            List<String> navigation) {
    }

    /** 一项能力开关：{@code enabled=false} 时前端隐藏对应菜单。 */
    public record Capability(String key, String label, boolean enabled, String reason) {
    }

    /** {@code GET /api/v1/health}。 */
    public record Health(
            boolean ready,
            String product,
            String abbr,
            String version,
            String dialect,
            String dialectDetail,
            long skillCount,
            String libraryVersion,
            String startedAt,
            String schemaVersion,
            List<String> capabilities,
            Map<String, Object> extra) {
    }

    /** 首页统计。 */
    public record Stats(
            long skillCount,
            long publishedCount,
            long archivedCount,
            long versionCount,
            long categoryCount,
            long contributorCount,
            long downloadTotal,
            long contributionPending,
            long riskSignalCount,
            List<Distribution> riskDistribution,
            List<Distribution> categoryDistribution,
            List<Distribution> sceneDistribution,
            List<Distribution> sourceDistribution,
            List<Distribution> authorKindDistribution) {
    }

    /** 一个分布项。 */
    public record Distribution(String key, String label, long count) {
    }

    /** 分类字典项（含计数）。 */
    public record CategoryItem(String code, String label, String kind, String sceneCode, long count) {
    }

    /** 文档目录项。 */
    public record DocItem(String docKey, String title, String section, String summary, int sortNo) {
    }

    /** 文档正文。 */
    public record DocDetail(String docKey, String title, String section, String summary, String body,
                            String updatedAt) {
    }

    /** 贡献者列表项。 */
    public record ContributorItem(String source, String author, String name, String kind,
                                 String kindLabel, String description, String gitProfile,
                                 long skillCount, long downloadTotal, String createdAt) {
    }

    /** 贡献者列表（含分类计数）。 */
    public record ContributorPage(long page, long size, long total, long totalPages,
                                  Map<String, Long> kindCounts, List<ContributorItem> items) {
    }

    /** 贡献者详情：档案 + 其全部 Skill 卡片。 */
    public record ContributorDetail(ContributorItem profile, List<SkillDtos.Card> skills) {
    }

    /** 操作审计流水项。 */
    public record OpLogItem(long id, String actor, String role, String action, String targetType,
                            String targetId, String detail, String traceId, String createdAt) {
    }

    /** 关键词检索命中（带打分，便于前端排序与高亮）。 */
    public record SearchHit(SkillDtos.Card card, int score, String matchedIn) {
    }
}
