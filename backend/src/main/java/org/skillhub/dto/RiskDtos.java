package org.skillhub.dto;

import java.util.List;
import java.util.Map;

/** 风险评估的形状。 */
public final class RiskDtos {

    private RiskDtos() {
    }

    /** 指标字典（{@code GET /api/v1/risk/taxonomy}）。 */
    public record Taxonomy(String version, int dimensionCount, int indicatorCount,
                           List<Map<String, Object>> dimensions,
                           List<Map<String, Object>> severities,
                           List<Map<String, Object>> indicators,
                           List<Map<String, Object>> levels,
                           String scoreFormula, String levelRule) {
    }

    /** 在线试算请求。 */
    public record ScanRequest(String content, String fileName) {
    }

    /** 在线试算结果。 */
    public record ScanResult(int riskScore, String riskLevel, String riskLabel,
                             List<SkillDtos.DimensionScore> dimensions,
                             Map<String, Integer> severityCounts,
                             int signalTotal, List<SkillDtos.Signal> signals,
                             String conclusion) {
    }
}
