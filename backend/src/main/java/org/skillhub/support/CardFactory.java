package org.skillhub.support;

import java.util.List;
import org.skillhub.dto.SkillDtos;
import org.skillhub.model.Skill;
import org.springframework.stereotype.Component;

/**
 * 把 {@link Skill} 行数据装配成对外卡片，并补齐"标签化"字段
 * （分类中文名、场景中文名、贡献者类型中文名、风险等级中文名）。
 *
 * <p>集中在一个地方做，避免每个 Controller/Service 各写一遍导致三系统文案漂移。
 */
@Component
public class CardFactory {

    /** 场景 code → 中文名。 */
    private static final java.util.Map<String, String> SCENE_LABELS = java.util.Map.of(
            "general", "通用",
            "edu", "教育",
            "fin", "金融",
            "gov", "政务与法务",
            "med", "医疗健康",
            "news", "新闻传媒",
            "tra", "交通物流");

    public SkillDtos.Card card(Skill s) {
        if (s == null) {
            return null;
        }
        return new SkillDtos.Card(
                s.skillId,
                s.slug,
                s.name,
                blankToDash(s.title),
                blankToDash(s.description),
                s.categoryCode,
                Categories.label(s.categoryCode),
                s.sceneCode,
                sceneLabel(s.sceneCode),
                Texts.splitTags(s.tags),
                s.version,
                s.source,
                s.sourceUrl,
                s.sourceRepo,
                blankToDash(s.license),
                blankToDash(s.author),
                s.authorKind,
                authorKindLabel(s.authorKind),
                s.riskScore == null ? 0 : s.riskScore,
                blankToDash(s.riskLevel),
                RiskScanner.levelLabel(s.riskLevel),
                s.status,
                nz(s.downloadCount),
                nz(s.installCount),
                nz(s.starCount),
                nz(s.fileCount),
                s.packageBytes == null ? 0L : s.packageBytes,
                nz(s.contentBytes),
                nz(s.evalCount),
                s.avgScore == null ? 0d : s.avgScore.doubleValue(),
                Dates.format(s.createdAt),
                Dates.format(s.updatedAt));
    }

    public List<SkillDtos.Card> cards(List<Skill> skills) {
        return skills == null ? List.of() : skills.stream().map(this::card).toList();
    }

    public static String sceneLabel(String code) {
        return SCENE_LABELS.getOrDefault(code == null ? "general" : code, "通用");
    }

    public static String authorKindLabel(String kind) {
        return "personal".equals(kind) ? "个人" : "部门/企业";
    }

    public static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "published" -> "已发布";
            case "draft" -> "草稿";
            case "submitted" -> "待发布";
            case "rejected" -> "已驳回";
            case "archived" -> "已归档";
            default -> status;
        };
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String blankToDash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }
}
