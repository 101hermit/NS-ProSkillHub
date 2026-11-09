package org.skillhub.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 风险指标字典：加载 {@code classpath:risk/indicators.json}（6 维度 / 32 指标）。
 *
 * <p>口径对齐 openEuler SkillHub 的 SkillSpector。字典是三系统逐字节一致的资源文件，
 * 由 {@code RiskScanner} 消费；本类同时在启动时把正则**预编译**，扫描时不再付出编译成本。
 */
@Component
public class RiskTaxonomy {

    /** 一个维度（风险类别）。 */
    public record Dimension(String key, String name, String desc) {
    }

    /** 一条指标。{@code pattern} 为 null 表示结构性规则，由 {@code structural} 标识判定方式。 */
    public record Indicator(String id, String name, String category, String severity, int weight,
                            String structural, Pattern pattern, String remediation) {
    }

    private final List<Dimension> dimensions;
    private final Map<String, String> severityLabels;
    private final List<Indicator> indicators;
    private final Map<String, Indicator> byId;
    private final Map<String, Dimension> dimensionByKey;

    public RiskTaxonomy(ObjectMapper mapper) {
        JsonNode root = read("risk/indicators.json");
        this.dimensions = new ArrayList<>();
        for (JsonNode c : root.path("categories")) {
            dimensions.add(new Dimension(text(c, "key"), text(c, "name"), text(c, "desc")));
        }
        this.dimensionByKey = new LinkedHashMap<>();
        for (Dimension d : dimensions) {
            dimensionByKey.put(d.key(), d);
        }
        this.severityLabels = new LinkedHashMap<>();
        for (JsonNode s : root.path("severities")) {
            severityLabels.put(text(s, "key"), text(s, "label"));
        }
        this.indicators = new ArrayList<>();
        this.byId = new LinkedHashMap<>();
        for (JsonNode n : root.path("indicators")) {
            String raw = n.hasNonNull("pattern") ? n.get("pattern").asText() : null;
            Indicator ind = new Indicator(
                    text(n, "id"),
                    text(n, "name"),
                    text(n, "category"),
                    text(n, "severity"),
                    n.path("weight").asInt(1),
                    n.hasNonNull("structural") ? n.get("structural").asText() : null,
                    raw == null || raw.isBlank() ? null : Pattern.compile(raw),
                    text(n, "remediation"));
            indicators.add(ind);
            byId.put(ind.id(), ind);
        }
        if (indicators.isEmpty()) {
            throw new IllegalStateException("risk/indicators.json 里没有任何指标，风险扫描无法工作");
        }
    }

    public List<Dimension> dimensions() {
        return dimensions;
    }

    public List<Indicator> indicators() {
        return indicators;
    }

    public Indicator indicator(String id) {
        return byId.get(id);
    }

    public Dimension dimension(String key) {
        Dimension d = dimensionByKey.get(key);
        return d == null ? new Dimension(key, key, "") : d;
    }

    public String severityLabel(String severity) {
        return severityLabels.getOrDefault(severity, severity);
    }

    /** 供 {@code GET /api/v1/risk/taxonomy} 原样输出（字段与 JSON 完全一致，便于前端自解释）。 */
    public Map<String, Object> describe() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dimension_count", dimensions.size());
        out.put("indicator_count", indicators.size());
        out.put("dimensions", dimensions.stream().map(d -> Map.of(
                "key", d.key(), "name", d.name(), "desc", d.desc())).toList());
        out.put("severities", severityLabels.entrySet().stream().map(e -> Map.of(
                "key", e.getKey(), "label", e.getValue())).toList());
        out.put("indicators", indicators.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id());
            m.put("name", i.name());
            m.put("category", i.category());
            m.put("dimension", i.category());
            m.put("dimension_name", dimension(i.category()).name());
            m.put("severity", i.severity());
            m.put("severity_label", severityLabel(i.severity()));
            m.put("weight", i.weight());
            m.put("structural", i.structural());
            m.put("remediation", i.remediation());
            return m;
        }).toList());
        out.put("levels", List.of(
                Map.of("level", "safe", "label", "安全", "min", 0, "max", 20),
                Map.of("level", "low", "label", "低风险", "min", 21, "max", 50),
                Map.of("level", "medium", "label", "中风险", "min", 51, "max", 80),
                Map.of("level", "high", "label", "高风险", "min", 81, "max", 100)));
        return out;
    }

    private JsonNode read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 " + path + " 失败", e);
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : "";
    }
}
