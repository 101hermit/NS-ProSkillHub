package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.dto.SiteDtos;
import org.skillhub.mapper.ContributorMapper;
import org.skillhub.mapper.ContributionMapper;
import org.skillhub.mapper.KeyCount;
import org.skillhub.mapper.RiskMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.mapper.SkillVersionMapper;
import org.skillhub.support.CardFactory;
import org.skillhub.support.Categories;
import org.springframework.stereotype.Service;

/**
 * 首页与仪表盘统计。全部来自 SQL 聚合，不做全表遍历。
 */
@Service
public class StatService {

    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final org.skillhub.mapper.CategoryMapper categoryMapper;
    private final ContributorMapper contributorMapper;
    private final ContributionMapper contributionMapper;
    private final RiskMapper riskMapper;

    public StatService(SkillMapper skillMapper, SkillVersionMapper versionMapper,
                       org.skillhub.mapper.CategoryMapper categoryMapper,
                       ContributorMapper contributorMapper,
                       ContributionMapper contributionMapper, RiskMapper riskMapper) {
        this.skillMapper = skillMapper;
        this.versionMapper = versionMapper;
        this.categoryMapper = categoryMapper;
        this.contributorMapper = contributorMapper;
        this.contributionMapper = contributionMapper;
        this.riskMapper = riskMapper;
    }

    public SiteDtos.Stats stats() {
        long total = skillMapper.countAll();
        long published = skillMapper.countByStatus("published");
        long archived = skillMapper.countByStatus("archived");
        long versions = versionMapper.countAll();
        long categories = categoryMapper.countAll();
        long contributors = contributorMapper.countAll();
        long downloads = skillMapper.sumDownloads();
        long pending = contributionMapper.count("submitted", null);
        long signals = riskMapper.countSignals();

        return new SiteDtos.Stats(
                total, published, archived, versions,
                categories == 0 ? Categories.ALL.size() : categories,
                contributors, downloads, pending, signals,
                toDistributions(skillMapper.groupByRiskLevel(), "risk"),
                toDistributions(skillMapper.groupByCategory(), "category"),
                toDistributions(skillMapper.groupByScene(), "scene"),
                toDistributions(skillMapper.groupBySource(), "source"),
                toDistributions(skillMapper.groupByAuthorKind(), "authorKind"));
    }

    /** 把 {@code {k,c}} 行翻译成带中文标签的分布项。 */
    private List<SiteDtos.Distribution> toDistributions(List<KeyCount> rows, String kind) {
        List<SiteDtos.Distribution> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (KeyCount row : rows) {
            if (row.k == null) {
                continue;
            }
            out.add(new SiteDtos.Distribution(row.k, label(kind, row.k), row.c == null ? 0 : row.c));
        }
        out.sort((a, b) -> Long.compare(b.count(), a.count()));
        return out;
    }

    private static String label(String kind, String key) {
        return switch (kind) {
            case "risk" -> org.skillhub.support.RiskScanner.levelLabel(key);
            case "category" -> Categories.label(key);
            case "scene" -> CardFactory.sceneLabel(key);
            case "authorKind" -> CardFactory.authorKindLabel(key);
            case "source" -> switch (key) {
                case "library" -> "本地库";
                case "github" -> "GitHub";
                case "gitee" -> "Gitee";
                case "gitlab" -> "GitLab";
                case "gitcode" -> "GitCode";
                case "upload" -> "在线上传";
                default -> key;
            };
            default -> key;
        };
    }

    /** 只保留出现过的分类（用于首页"分类格子"）。 */
    public Map<String, Long> categoryCounts() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (KeyCount row : skillMapper.groupByCategory()) {
            if (row.k != null) {
                out.put(row.k, row.c == null ? 0L : row.c.longValue());
            }
        }
        return out;
    }
}
