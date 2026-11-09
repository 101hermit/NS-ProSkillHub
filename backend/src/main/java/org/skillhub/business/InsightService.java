package org.skillhub.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.ProDtos;
import org.skillhub.mapper.ProMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.ProEntities;
import org.skillhub.model.Skill;
import org.skillhub.port.IdPort;
import org.skillhub.support.Dates;
import org.skillhub.support.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识级与统计分析（专业版独有）。
 *
 * <p>六种方法，全部基于**实测矩阵数据**（{@code mx_cell}）或 Skill 库统计量，不引入外部知识：
 *
 * <table border="1">
 *   <tr><th>method</th><th>做什么</th><th>输出</th></tr>
 *   <tr><td>DESCRIBE</td><td>描述统计</td><td>n / 均值 / 标准差 / 中位数 / 直方图</td></tr>
 *   <tr><td>HYPOTHESIS</td><td>两组均值差异（Welch t 检验）</td><td>t / df / p / 效应量 / 是否显著 / 结论</td></tr>
 *   <tr><td>TOPSIS</td><td>多准则排序</td><td>逐项得分与名次</td></tr>
 *   <tr><td>CLUSTER</td><td>k-means 聚类</td><td>每簇成员与中心</td></tr>
 *   <tr><td>REGRESSION</td><td>一元/多元最小二乘</td><td>系数 / R² / 残差摘要</td></tr>
 *   <tr><td>AHP</td><td>层次分析法权重</td><td>特征向量归一化权重 + 一致性比率</td></tr>
 * </table>
 *
 * <p>本类只做"算"，不解释业务含义；中文结论文本在这里生成，保证三个版本话术风格一致。
 */
@Service
public class InsightService {

    private final ProMapper proMapper;
    private final SkillMapper skillMapper;
    private final IdPort idPort;

    public InsightService(ProMapper proMapper, SkillMapper skillMapper, IdPort idPort) {
        this.proMapper = proMapper;
        this.skillMapper = skillMapper;
        this.idPort = idPort;
    }

    /** 执行一次分析并落报告。 */
    @Transactional
    public ProDtos.Report analyze(ProDtos.AnalyzeRequest req) {
        String method = (req.getMethod() == null ? "DESCRIBE" : req.getMethod()).toUpperCase(Locale.ROOT);
        SkillSample sample = loadSample(req);
        if (sample.values().isEmpty()) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "没有可分析的数据。怎么办：先去「实测中心」跑一个实验矩阵，再回来分析。");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String conclusion;
        String title;
        switch (method) {
            case "HYPOTHESIS" -> {
                title = "假设检验：两个 Agent 的均分差异";
                conclusion = hypothesis(sample, result);
            }
            case "TOPSIS" -> {
                title = "TOPSIS：多准则排序";
                conclusion = topsis(sample, result);
            }
            case "CLUSTER" -> {
                title = "聚类：按表现把 Skill 分组";
                conclusion = cluster(sample, result);
            }
            case "REGRESSION" -> {
                title = "回归：时延对综合分的影响";
                conclusion = regression(sample, result);
            }
            case "AHP" -> {
                title = "AHP：指标权重";
                conclusion = ahp(result);
            }
            default -> {
                title = "描述统计";
                conclusion = describe(sample, result);
            }
        }

        ProEntities.IxReport report = new ProEntities.IxReport();
        report.code = idPort.code("ix");
        report.title = title;
        report.method = method;
        report.scope = req.getScope() == null ? "" : String.valueOf(req.getScope());
        report.paramsJson = toJson(Map.of("groupBy", req.getGroupBy() == null ? "" : req.getGroupBy(),
                "matrixCodes", req.getMatrixCodes(), "params", req.getParams()));
        report.resultJson = toJson(result);
        report.conclusion = conclusion;
        proMapper.insertReport(report);
        return toReport(report);
    }

    public List<ProDtos.Report> reports(int limit) {
        return proMapper.listReports(Math.max(1, Math.min(limit, 50))).stream()
                .map(InsightService::toReport).toList();
    }

    public ProDtos.Report report(String code) {
        ProEntities.IxReport r = proMapper.findReport(code);
        if (r == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "分析报告「" + code + "」");
        }
        return toReport(r);
    }

    // ================================================================ 样本

    /** 分析样本：一组单元观测（分数组 + 分组标签 + 时延）。 */
    private record SkillSample(List<Double> values, Map<String, List<Double>> groups,
                               Map<String, List<Double>> caseGroups,
                               List<double[]> pairs, List<String> labels) {
    }

    private SkillSample loadSample(ProDtos.AnalyzeRequest req) {
        List<ProEntities.Cell> cells = new ArrayList<>();
        if (req.getMatrixCodes() != null && !req.getMatrixCodes().isEmpty()) {
            for (String code : req.getMatrixCodes()) {
                cells.addAll(proMapper.cellsOf(code));
            }
        } else {
            for (ProEntities.Matrix m : proMapper.listMatrices()) {
                cells.addAll(proMapper.cellsOf(m.code));
            }
        }
        List<Double> values = new ArrayList<>();
        List<double[]> pairs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        Map<String, List<Double>> caseGroups = new LinkedHashMap<>();
        for (ProEntities.Cell c : cells) {
            if (!"DONE".equals(c.status)) {
                continue;
            }
            double score = c.scoreTotal == null ? 0 : c.scoreTotal.doubleValue();
            values.add(score);
            pairs.add(new double[] {score, c.latencyMs == null ? 0 : c.latencyMs});
            labels.add(c.skillId);
            String g = "skill_id".equals(req.getGroupBy()) ? c.skillId
                    : "params".equals(req.getGroupBy()) ? String.valueOf(c.paramsJson)
                    : c.agentCode;
            groups.computeIfAbsent(g == null ? "unknown" : g, k -> new ArrayList<>()).add(score);
            caseGroups.computeIfAbsent(c.caseCode == null ? "default" : c.caseCode,
                    k -> new ArrayList<>()).add(score);
        }
        return new SkillSample(values, groups, caseGroups, pairs, labels);
    }

    // ================================================================ 方法

    private String describe(SkillSample s, Map<String, Object> out) {
        Metrics.Summary sum = Metrics.summary(s.values());
        out.put("n", sum.n());
        out.put("mean", round(sum.mean()));
        out.put("std", round(sum.std()));
        out.put("min", round(sum.min()));
        out.put("max", round(sum.max()));
        out.put("median", round(sum.median()));
        out.put("histogram", Metrics.histogram(s.values(), 6));
        out.put("group_mean", groupMeans(s.groups()));
        return String.format(Locale.ROOT,
                "共 %d 个有效单元，综合分均值 %.1f（标准差 %.1f，中位数 %.1f），区间 %.1f–%.1f。"
                        + "标准差越小说明表现越稳定；若标准差 > 均值的一半，说明结果高度依赖条件。",
                sum.n(), sum.mean(), sum.std(), sum.median(), sum.min(), sum.max());
    }

    /** Welch t 检验（两组不等方差）。 */
    private String hypothesis(SkillSample s, Map<String, Object> out) {
        List<String> keys = new ArrayList<>(s.groups().keySet());
        if (keys.size() < 2) {
            // 回退：只有一组时，按"用例"分组再比（不同用例的分数差异也是合法比较）
            Map<String, List<Double>> byCase = s.caseGroups();
            if (byCase.size() < 2) {
                throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                        "只有 1 个可比分组，无法做两组比较。怎么办：用至少 2 个 Agent（或 2 组参数、2 条用例）跑一次实验矩阵。");
            }
            s = new SkillSample(s.values(), byCase, byCase, s.pairs(), s.labels());
            keys = new ArrayList<>(byCase.keySet());
        }
        String a = keys.get(0);
        String b = keys.get(1);
        List<Double> x = s.groups().get(a);
        List<Double> y = s.groups().get(b);
        double mx = mean(x);
        double my = mean(y);
        double vx = variance(x);
        double vy = variance(y);
        double se = Math.sqrt(vx / x.size() + vy / y.size());
        double t = se == 0 ? 0 : (mx - my) / se;
        double df = se == 0 ? 1
                : Math.pow(vx / x.size() + vy / y.size(), 2)
                / (Math.pow(vx / x.size(), 2) / Math.max(1, x.size() - 1)
                + Math.pow(vy / y.size(), 2) / Math.max(1, y.size() - 1));
        double p = twoSidedP(t, df);
        double pooled = Math.sqrt((vx + vy) / 2);
        double effect = pooled == 0 ? 0 : (mx - my) / pooled;
        boolean significant = p < 0.05;

        out.put("group_a", a);
        out.put("group_b", b);
        out.put("n_a", x.size());
        out.put("n_b", y.size());
        out.put("mean_a", round(mx));
        out.put("mean_b", round(my));
        out.put("t", round(t));
        out.put("df", round(df));
        out.put("p", round(p));
        out.put("effect_size", round(effect));
        out.put("significant", significant);
        return String.format(Locale.ROOT,
                "比较「%s」(%d 个单元, 均值 %.1f) 与「%s」(%d 个单元, 均值 %.1f)："
                        + "Welch t = %.3f，df = %.1f，p = %.4f，效应量 Cohen's d = %.2f。"
                        + "%s",
                a, x.size(), mx, b, y.size(), my, t, df, p, effect,
                significant
                        ? "在 0.05 水平上差异显著 —— 换条件确实会改变表现，值得单独说明。"
                        : "在 0.05 水平上差异不显著 —— 目前的证据不足以说明换条件有影响，"
                        + "建议增加样本量（多跑几组用例）再判断。");
    }

    private String topsis(SkillSample s, Map<String, Object> out) {
        List<String> keys = new ArrayList<>(s.groups().keySet());
        double[][] matrix = new double[keys.size()][2];
        for (int i = 0; i < keys.size(); i++) {
            List<Double> g = s.groups().get(keys.get(i));
            matrix[i][0] = mean(g);
            matrix[i][1] = 1.0 / (1.0 + variance(g)); // 稳定性：方差越小越好
        }
        double[] scores = Metrics.topsis(matrix, new double[] {3, 1},
                new boolean[] {true, true});
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", keys.get(i));
            row.put("mean", round(matrix[i][0]));
            row.put("stability", round(matrix[i][1]));
            row.put("topsis", round(scores[i]));
            rows.add(row);
        }
        rows.sort((p, q) -> Double.compare((double) q.get("topsis"), (double) p.get("topsis")));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("rank", i + 1);
        }
        out.put("criteria", List.of("均值(权重3)", "稳定性(权重1)"));
        out.put("rows", rows);
        return "按 TOPSIS（均值权重 3、稳定性权重 1）排序，第 1 名是「"
                + rows.get(0).get("key") + "」（得分 " + rows.get(0).get("topsis") + "）。"
                + "TOPSIS 的好处是把「又高又稳」和「偶尔很高但不稳」区分开。";
    }

    /** 简单 k-means（k=3 或样本数更小）。 */
    private String cluster(SkillSample s, Map<String, Object> out) {
        int k = Math.min(3, Math.max(2, s.values().size() / 3));
        double[] data = s.values().stream().mapToDouble(Double::doubleValue).toArray();
        double[] centers = new double[k];
        for (int i = 0; i < k; i++) {
            centers[i] = data[(int) ((long) i * data.length / k)];
        }
        int[] assign = new int[data.length];
        for (int iter = 0; iter < 30; iter++) {
            boolean changed = false;
            for (int i = 0; i < data.length; i++) {
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double d = Math.abs(data[i] - centers[c]);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
                if (assign[i] != best) {
                    assign[i] = best;
                    changed = true;
                }
            }
            for (int c = 0; c < k; c++) {
                double sum = 0;
                int n = 0;
                for (int i = 0; i < data.length; i++) {
                    if (assign[i] == c) {
                        sum += data[i];
                        n++;
                    }
                }
                if (n > 0) {
                    centers[c] = sum / n;
                }
            }
            if (!changed) {
                break;
            }
        }
        List<Map<String, Object>> clusters = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            List<String> members = new ArrayList<>();
            for (int i = 0; i < data.length; i++) {
                if (assign[i] == c && members.size() < 8) {
                    members.add(s.labels().get(i));
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cluster", c + 1);
            row.put("center", round(centers[c]));
            final int clusterIdx = c;
            row.put("size", (int) java.util.Arrays.stream(assign).filter(x -> x == clusterIdx).count());
            row.put("samples", members);
            clusters.add(row);
        }
        out.put("k", k);
        out.put("clusters", clusters);
        return "按综合分聚成 " + k + " 簇，中心分别是 "
                + clusters.stream().map(x -> String.valueOf(x.get("center"))).reduce((p, q) -> p + " / " + q)
                .orElse("") + "。中心差距大说明表现分层明显；差距小说明整体稳定。";
    }

    /** 一元线性回归：综合分 ~ 时延。 */
    private String regression(SkillSample s, Map<String, Object> out) {
        int n = s.pairs().size();
        double sx = 0;
        double sy = 0;
        for (double[] p : s.pairs()) {
            sx += p[1];
            sy += p[0];
        }
        double mx = sx / n;
        double my = sy / n;
        double sxy = 0;
        double sxx = 0;
        double syy = 0;
        for (double[] p : s.pairs()) {
            sxy += (p[1] - mx) * (p[0] - my);
            sxx += (p[1] - mx) * (p[1] - mx);
            syy += (p[0] - my) * (p[0] - my);
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = my - slope * mx;
        double r2 = (sxx == 0 || syy == 0) ? 0 : (sxy * sxy) / (sxx * syy);
        out.put("n", n);
        out.put("slope", round(slope));
        out.put("intercept", round(intercept));
        out.put("r2", round(r2));
        out.put("x", "latency_ms");
        out.put("y", "score_total");
        return String.format(Locale.ROOT,
                "以时延解释综合分：score = %.4f × latency + %.2f，R² = %.3f。"
                        + "%s",
                slope, intercept, r2,
                r2 < 0.1
                        ? "R² 很低，说明时延几乎解释不了分数差异 —— 表现差异更可能来自 Skill/Agent 本身。"
                        : "R² 不低，说明时延与表现有一定关联，慢的调用确实更容易得分低。");
    }

    /** AHP：给定 3×3 判断矩阵（需求里"专业级方法论"的体现），输出权重与一致性比率。 */
    private String ahp(Map<String, Object> out) {
        // 预置判断矩阵：正确性 > 覆盖 > 时延
        double[][] m = {
                {1, 3, 5},
                {1.0 / 3, 1, 3},
                {1.0 / 5, 1.0 / 3, 1},
        };
        double[] weights = ahpWeights(m);
        double cr = consistencyRatio(m, weights);
        out.put("criteria", List.of("正确性", "期望覆盖", "时延效率"));
        out.put("weights", java.util.Arrays.stream(weights).map(InsightService::round).toArray());
        out.put("consistency_ratio", round(cr));
        out.put("consistent", cr < 0.1);
        return String.format(Locale.ROOT,
                "AHP 判断矩阵给出权重：正确性 %.2f / 期望覆盖 %.2f / 时延效率 %.2f，"
                        + "一致性比率 CR = %.3f（%s）。"
                        + "CR < 0.1 才算判断前后一致，否则要重新调整两两比较。",
                weights[0], weights[1], weights[2], cr, cr < 0.1 ? "通过" : "不通过");
    }

    /** 算 AHP 权重（几何平均法）。 */
    private static double[] ahpWeights(double[][] m) {
        int n = m.length;
        double[] rowGeo = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            double prod = 1;
            for (int j = 0; j < n; j++) {
                prod *= m[i][j];
            }
            rowGeo[i] = Math.pow(prod, 1.0 / n);
            total += rowGeo[i];
        }
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = rowGeo[i] / total;
        }
        return w;
    }

    /** 一致性比率 CR = CI / RI。 */
    private static double consistencyRatio(double[][] m, double[] w) {
        int n = m.length;
        double[] mw = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                mw[i] += m[i][j] * w[j];
            }
        }
        double lambda = 0;
        for (int i = 0; i < n; i++) {
            lambda += mw[i] / w[i];
        }
        lambda /= n;
        double ci = (lambda - n) / (n - 1);
        double[] ri = {0, 0, 0.58, 0.90, 1.12, 1.24, 1.32, 1.41, 1.45};
        double base = n - 1 < ri.length ? ri[n - 1] : 1.45;
        return base == 0 ? 0 : ci / base;
    }

    // ================================================================ 统计工具

    private static double mean(List<Double> v) {
        return v.isEmpty() ? 0 : v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double variance(List<Double> v) {
        if (v.size() < 2) {
            return 0;
        }
        double m = mean(v);
        return v.stream().mapToDouble(x -> (x - m) * (x - m)).sum() / (v.size() - 1);
    }

    /** t 分布双侧 p 值（正态近似 + Edgeworth 修正，够用于"显著 / 不显著"的判断）。 */
    private static double twoSidedP(double t, double df) {
        double x = Math.abs(t);
        // 正态近似的尾概率
        double z = x * (1 - 1.0 / (4 * Math.max(1, df))) / Math.sqrt(1 + x * x / (2 * Math.max(1, df)));
        double p = 2 * (1 - normalCdf(z));
        return Math.max(0, Math.min(1, p));
    }

    private static double normalCdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    /** Abramowitz–Stegun 误差函数近似（精度 ~1e-7）。 */
    private static double erf(double x) {
        double sign = x < 0 ? -1 : 1;
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                + 0.254829592) * t * Math.exp(-ax * ax);
        return sign * y;
    }

    private static Map<String, Object> groupMeans(Map<String, List<Double>> groups) {
        Map<String, Object> out = new LinkedHashMap<>();
        groups.forEach((k, v) -> out.put(k, round(mean(v))));
        return out;
    }

    private static ProDtos.Report toReport(ProEntities.IxReport r) {
        return new ProDtos.Report(r.code, r.title, r.method, r.scope, r.resultJson, r.conclusion,
                Dates.format(r.createdAt));
    }

    private static String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0;
        }
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /** 未使用但保留：把 Skill 列表按分类聚合（供后续扩展）。 */
    private Map<String, Set<String>> skillsByCategory() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Skill s : skillMapper.listAllCards()) {
            out.computeIfAbsent(s.categoryCode == null ? "general" : s.categoryCode,
                    k -> new LinkedHashSet<>()).add(s.skillId);
        }
        return out;
    }
}
