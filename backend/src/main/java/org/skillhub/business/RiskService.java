package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.dto.RiskDtos;
import org.skillhub.dto.SkillDtos;
import org.skillhub.support.RiskScanner;
import org.skillhub.support.RiskTaxonomy;
import org.springframework.stereotype.Service;

/**
 * 风险评估业务：把"指标字典"和"扫描引擎"包装成对外的两个能力。
 *
 * <p>资源站的全部"评估"能力就只有这两个端点 —— 它不做在线实测、不做统计推断，
 * 这是 {@code docs/00 §2.1} 划定的能力边界。
 */
@Service
public class RiskService {

    private final RiskTaxonomy taxonomy;
    private final RiskScanner scanner;

    public RiskService(RiskTaxonomy taxonomy, RiskScanner scanner) {
        this.taxonomy = taxonomy;
        this.scanner = scanner;
    }

    /** 指标字典（原样输出，供前端自解释）。 */
    public RiskDtos.Taxonomy taxonomy() {
        Map<String, Object> raw = taxonomy.describe();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) raw.get("dimensions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> severities = (List<Map<String, Object>>) raw.get("severities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indicators = (List<Map<String, Object>>) raw.get("indicators");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) raw.get("levels");
        return new RiskDtos.Taxonomy("1.0",
                taxonomy.dimensions().size(),
                taxonomy.indicators().size(),
                dimensions, severities, indicators, levels,
                "命中指标的权重累加，封顶 100 分（同一指标最多记 5 处，明细合计最多 40 条）",
                "0-20 安全 / 21-50 低风险 / 51-80 中风险 / 81-100 高风险；无评估记录为「未检测」");
    }

    /** 在线试算：把用户贴进来的文本当成一份 SKILL.md 扫描。 */
    public RiskDtos.ScanResult scan(String content, String fileName) {
        RiskScanner.Report report = scanner.scan(
                fileName == null || fileName.isBlank() ? "SKILL.md" : fileName,
                content == null ? "" : content);
        return toScanResult(report);
    }

    /** 把引擎输出转成对外形状（供详情页与贡献预评估复用）。 */    public RiskDtos.ScanResult toScanResult(RiskScanner.Report report) {
        List<SkillDtos.Signal> signals = new ArrayList<>(report.hits().size());
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        Map<String, Integer> dimensionSignalCount = new LinkedHashMap<>();
        for (RiskScanner.Hit h : report.hits()) {
            severityCounts.merge(h.severity(), 1, Integer::sum);
            dimensionSignalCount.merge(h.dimension(), 1, Integer::sum);
            signals.add(new SkillDtos.Signal(
                    h.code(), h.indicator(), h.dimension(),
                    taxonomy.dimension(h.dimension()).name(),
                    h.severity(), taxonomy.severityLabel(h.severity()),
                    h.weight(), h.filePath(), h.lineNo(), h.remediation()));
        }
        List<SkillDtos.DimensionScore> dims = new ArrayList<>();
        for (RiskTaxonomy.Dimension d : taxonomy.dimensions()) {
            dims.add(new SkillDtos.DimensionScore(d.key(), d.name(),
                    report.dimensionScores().getOrDefault(d.key(), 0),
                    dimensionSignalCount.getOrDefault(d.key(), 0)));
        }
        return new RiskDtos.ScanResult(
                report.score(), report.level(), RiskScanner.levelLabel(report.level()),
                dims, severityCounts, report.signalTotal(), signals,
                conclusion(report));
    }

    /** 一句话结论：让不懂指标的读者也知道"能不能用"。 */
    private static String conclusion(RiskScanner.Report report) {
        return switch (report.level()) {
            case "safe" -> "未发现高风险行为，可以直接使用。仍建议在使用前通读 SKILL.md。";
            case "low" -> "发现少量可疑写法，通常是正常的工具调用。使用前建议确认它访问了哪些外部服务。";
            case "medium" -> "存在需要人工确认的行为（读写文件、执行命令、访问网络）。建议在隔离环境先试跑。";
            case "high" -> "存在高危行为特征（例如远程下载并执行、提权、读取凭据）。不建议在生产环境直接使用。";
            default -> "未能得出等级。";
        };
    }

    /**
     * 把数据库里的评估记录装配成对外形状。
     *
     * @param audit   {@code risk_audit} 行；为 {@code null} 表示"未检测"
     * @param signals {@code risk_signal} 行
     */
    public SkillDtos.Audit toAudit(org.skillhub.model.RiskAudit audit,
                                   List<org.skillhub.model.RiskSignal> signals) {
        if (audit == null) {
            return new SkillDtos.Audit(0, "unrated", RiskScanner.levelLabel("unrated"), "-",
                    List.of(), Map.of(), 0, "", "这个 Skill 还没有评估记录。", "", List.of());
        }
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        Map<String, Integer> dimSignal = new LinkedHashMap<>();
        List<SkillDtos.Signal> items = new ArrayList<>();
        List<org.skillhub.model.RiskSignal> safeSignals = signals == null ? List.of() : signals;
        for (org.skillhub.model.RiskSignal s : safeSignals) {
            severityCounts.merge(s.severity, 1, Integer::sum);
            dimSignal.merge(s.dimension, 1, Integer::sum);
            items.add(new SkillDtos.Signal(s.signalCode, s.indicator, s.dimension,
                    taxonomy.dimension(s.dimension).name(), s.severity,
                    taxonomy.severityLabel(s.severity), s.weight == null ? 0 : s.weight,
                    s.filePath, s.lineNo == null ? 0 : s.lineNo, s.remediation));
        }
        List<SkillDtos.DimensionScore> dims = new ArrayList<>();
        Map<String, Integer> dimScores = parseDimensionScores(audit.dimensionJson);
        for (RiskTaxonomy.Dimension d : taxonomy.dimensions()) {
            dims.add(new SkillDtos.DimensionScore(d.key(), d.name(),
                    dimScores.getOrDefault(d.key(), 0), dimSignal.getOrDefault(d.key(), 0)));
        }
        return new SkillDtos.Audit(
                audit.riskScore == null ? 0 : audit.riskScore,
                audit.riskLevel,
                RiskScanner.levelLabel(audit.riskLevel),
                audit.engine,
                dims, severityCounts,
                audit.signalTotal == null ? 0 : audit.signalTotal,
                audit.summary == null ? "" : audit.summary,
                audit.conclusion == null ? "" : audit.conclusion,
                org.skillhub.support.Dates.format(audit.createdAt),
                items);
    }

    /** {@code dimension_json} 解析失败不该让详情页崩掉，返回空表即可。 */
    private static Map<String, Integer> parseDimensionScores(String json) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asInt(0)));
        } catch (Exception ignore) {
            return out;
        }
        return out;
    }
}
