package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.SiteDtos;
import org.skillhub.dto.SkillDtos;
import org.skillhub.mapper.ContributorMapper;
import org.skillhub.mapper.KeyCount;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.Contributor;
import org.skillhub.support.CardFactory;
import org.skillhub.support.Dates;
import org.springframework.stereotype.Service;

/**
 * 贡献者：把"谁贡献了什么"讲清楚。
 *
 * <p>两种身份（doc 要求）：
 * <ul>
 *   <li>{@code personal} 个人创作者</li>
 *   <li>{@code department} 部门 / 企业创作者</li>
 * </ul>
 * 身份存在 {@code contributor.kind}，不靠 {@code platform} 派生 —— 少一个概念就少一处歧义。
 */
@Service
public class ContributorService {

    private final ContributorMapper contributorMapper;
    private final SkillMapper skillMapper;
    private final CardFactory cardFactory;

    public ContributorService(ContributorMapper contributorMapper, SkillMapper skillMapper,
                              CardFactory cardFactory) {
        this.contributorMapper = contributorMapper;
        this.skillMapper = skillMapper;
        this.cardFactory = cardFactory;
    }

    public SiteDtos.ContributorPage page(int page, int size, String kind) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(1, page);
        long total = contributorMapper.count(kind);
        Map<String, Long> skillCounts = toMap(skillMapper.groupCountByAuthor());
        Map<String, Long> downloadCounts = toMap(skillMapper.groupDownloadsByAuthor());
        Map<String, Long> kindCounts = new LinkedHashMap<>();
        for (KeyCount row : contributorMapper.groupByKind()) {
            if (row.k != null) {
                kindCounts.put(row.k, row.c == null ? 0L : row.c.longValue());
            }
        }
        if (total == 0) {
            return new SiteDtos.ContributorPage(safePage, safeSize, 0, 0, kindCounts, List.of());
        }
        List<SiteDtos.ContributorItem> items = new ArrayList<>();
        for (Contributor c : contributorMapper.findPage(kind, safeSize, (safePage - 1) * safeSize)) {
            items.add(toItem(c, skillCounts.getOrDefault(c.author, 0L),
                    downloadCounts.getOrDefault(c.author, 0L)));
        }
        int pages = (int) ((total + safeSize - 1) / safeSize);
        return new SiteDtos.ContributorPage(safePage, safeSize, total, pages, kindCounts, items);
    }

    public SiteDtos.ContributorDetail detail(String source, String author) {
        Contributor c = contributorMapper.findOne(source, author);
        if (c == null) {
            // 有些 Skill 的作者只在 skill.author 里出现过，没登记成 contributor；这里做一次兜底
            List<SkillDtos.Card> skills = cardFactory.cards(skillMapper.findCardsByAuthor(author, 200));
            if (skills.isEmpty()) {
                throw ApiException.of(ErrorCode.NOT_FOUND, "贡献者「" + author + "」");
            }
            long downloads = skills.stream().mapToLong(SkillDtos.Card::downloadCount).sum();
            SiteDtos.ContributorItem ghost = new SiteDtos.ContributorItem(source, author, author,
                    "department", CardFactory.authorKindLabel("department"),
                    "这位作者的作品来自 Skill 库的来源仓库，尚未单独建档。", "", skills.size(), downloads, "");
            return new SiteDtos.ContributorDetail(ghost, skills);
        }
        Map<String, Long> skillCounts = toMap(skillMapper.groupCountByAuthor());
        Map<String, Long> downloadCounts = toMap(skillMapper.groupDownloadsByAuthor());
        SiteDtos.ContributorItem item = toItem(c,
                skillCounts.getOrDefault(c.author, 0L), downloadCounts.getOrDefault(c.author, 0L));
        return new SiteDtos.ContributorDetail(item,
                cardFactory.cards(skillMapper.findCardsByAuthor(c.author, 200)));
    }

    /** 登记或更新贡献者（contributions 发布时调用）。 */
    public void upsert(String source, String author, String name, String kind, String description) {
        Contributor existing = contributorMapper.findOne(source, author);
        Contributor c = new Contributor();
        c.source = source;
        c.author = author;
        c.name = name == null || name.isBlank() ? author : name;
        c.kind = "personal".equals(kind) ? "personal" : "department";
        c.description = description == null ? "" : description;
        c.gitProfile = "";
        if (existing == null) {
            contributorMapper.insert(c);
        } else {
            contributorMapper.update(c);
        }
    }

    private static SiteDtos.ContributorItem toItem(Contributor c, long skillCount, long downloadTotal) {
        return new SiteDtos.ContributorItem(
                c.source, c.author, c.name, c.kind, CardFactory.authorKindLabel(c.kind),
                c.description == null ? "" : c.description,
                c.gitProfile == null ? "" : c.gitProfile,
                skillCount, downloadTotal, Dates.format(c.createdAt));
    }

    private static Map<String, Long> toMap(List<KeyCount> rows) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (KeyCount row : rows) {
            if (row.k != null) {
                out.put(row.k, row.c == null ? 0L : row.c.longValue());
            }
        }
        return out;
    }
}
