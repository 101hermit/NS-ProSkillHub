package org.skillhub.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;

/**
 * 静态风险扫描引擎：把一段文本（SKILL.md + 附件文本）映射成 0-100 风险分与信号清单。
 *
 * <p><b>确定性</b>：同一份文本永远得到同一个结果 —— 这是本引擎能被"验收"的前提。
 * 不调用任何大模型，纯本地正则 + 结构性规则。
 *
 * <p>评分口径（与 openEuler SkillHub 对齐）：
 * <ol>
 *   <li>每条指标最多记录 5 处命中，全部指标合计最多 40 条明细</li>
 *   <li>分数 = 命中指标的权重之和，封顶 100</li>
 *   <li>0-20 安全 / 21-50 低 / 51-80 中 / 81-100 高</li>
 * </ol>
 */
@Component
public class RiskScanner {

    private static final int MAX_HITS_PER_INDICATOR = 5;
    private static final int MAX_SIGNALS_TOTAL = 40;

    /** 单条命中信号。 */
    public record Hit(String code, String indicator, String dimension, String severity, int weight,
                      String filePath, int lineNo, String remediation) {
    }

    /** 一次扫描的完整结果。 */
    public record Report(int score, String level, int signalTotal,
                         Map<String, Integer> dimensionScores, List<Hit> hits) {
    }

    private final RiskTaxonomy taxonomy;

    public RiskScanner(RiskTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    /**
     * @param fileName 展示用的文件名（例如 {@code SKILL.md}）
     * @param text     待扫描文本
     */
    public Report scan(String fileName, String text) {
        return scan(List.of(new Doc(fileName, text == null ? "" : text, true)));
    }

    /** 待扫描的文档；{@code primary=true} 的那份参与"结构性规则"判定。 */
    public record Doc(String name, String text, boolean primary) {
    }

    /** 多文档扫描（SKILL.md + bundle 内文本）。 */
    public Report scan(List<Doc> docs) {
        List<Hit> hits = new ArrayList<>();
        Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        int score = 0;

        for (RiskTaxonomy.Indicator ind : taxonomy.indicators()) {
            int found = 0;
            if (ind.pattern() != null) {
                for (Doc doc : docs) {
                    if (found >= MAX_HITS_PER_INDICATOR || hits.size() >= MAX_SIGNALS_TOTAL) {
                        break;
                    }
                    found += matchInDoc(ind, doc, hits);
                }
            } else if (ind.structural() != null) {
                found = structural(ind, docs, hits);
            }
            if (found > 0) {
                score += ind.weight();
                dimensionScores.merge(ind.category(), ind.weight(), Integer::sum);
            }
        }

        int capped = Math.min(100, score);
        return new Report(capped, levelOf(capped), hits.size(), dimensionScores, hits);
    }

    /** 分数 → 等级（{@code unrated} 只在没有任何评估记录时使用，不在这里产生）。 */
    public static String levelOf(int score) {
        if (score <= 20) {
            return "safe";
        }
        if (score <= 50) {
            return "low";
        }
        if (score <= 80) {
            return "medium";
        }
        return "high";
    }

    public static String levelLabel(String level) {
        return switch (level == null ? "" : level) {
            case "safe" -> "安全";
            case "low" -> "低风险";
            case "medium" -> "中风险";
            case "high" -> "高风险";
            default -> "未检测";
        };
    }

    private int matchInDoc(RiskTaxonomy.Indicator ind, Doc doc, List<Hit> hits) {
        if (doc.text().isEmpty()) {
            return 0;
        }
        Matcher m = ind.pattern().matcher(doc.text());
        int count = 0;
        while (m.find()) {
            count++;
            hits.add(new Hit(ind.id(), ind.name(), ind.category(), ind.severity(), ind.weight(),
                    doc.name(), lineOf(doc.text(), m.start()), ind.remediation()));
            if (count >= MAX_HITS_PER_INDICATOR || hits.size() >= MAX_SIGNALS_TOTAL) {
                break;
            }
        }
        return count;
    }

    /** 结构性规则：目前只有 {@code no-permissions-declared}（SKILL.md 未声明权限/工具）。 */
    private int structural(RiskTaxonomy.Indicator ind, List<Doc> docs, List<Hit> hits) {
        Doc primary = docs.stream().filter(Doc::primary).findFirst()
                .orElse(docs.isEmpty() ? new Doc("SKILL.md", "", true) : docs.get(0));
        if (!"no-permissions-declared".equals(ind.structural())) {
            return 0;
        }
        String head = primary.text();
        boolean declared = head.contains("allowed-tools") || head.contains("allowed_tools")
                || head.contains("permissions:") || head.contains("permission:")
                || head.contains("tools:");
        if (declared) {
            return 0;
        }
        hits.add(new Hit(ind.id(), ind.name(), ind.category(), ind.severity(), ind.weight(),
                primary.name(), 1, ind.remediation()));
        return 1;
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
