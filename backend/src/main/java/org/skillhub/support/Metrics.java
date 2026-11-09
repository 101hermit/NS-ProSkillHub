package org.skillhub.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 评估指标与简单数理方法（**只用统计学与最优化，不含任何知识级处理**）。
 *
 * <p>这是标准版的能力边界（见 docs/00 §2.2）：只有描述统计、加权评分、z-score 归一化、
 * TOPSIS 多准则排序。知识图谱、嵌入向量、聚类、假设检验等属于专业版。
 *
 * <p>所有方法都是纯函数，可直接单测（`MetricsTest`）。
 */
public final class Metrics {

    private Metrics() {
    }

    // ---------------------------------------------------------------- 分词
    /**
     * 极简"分词"：英文按词、中文按**字二元组**（bigram）。
     *
     * <p>为什么不用词典分词：站内要处理中英混排且不想引依赖；bigram 对"是否说到了同一件事"
     * 这个粒度足够，且对分词错误不敏感（不会因为切错一个词全盘错）。
     */
    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String s = text.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                flushAscii(ascii, out);
                cjk.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjk(cjk, out);
                ascii.append(ch);
            } else {
                flushAscii(ascii, out);
                flushCjk(cjk, out);
            }
        }
        flushAscii(ascii, out);
        flushCjk(cjk, out);
        return out;
    }

    private static void flushAscii(StringBuilder buf, List<String> out) {
        if (buf.length() > 0) {
            if (buf.length() > 1) {
                out.add(buf.toString());
            }
            buf.setLength(0);
        }
    }

    private static void flushCjk(StringBuilder buf, List<String> out) {
        if (buf.length() == 1) {
            out.add(buf.toString());
        } else if (buf.length() > 1) {
            for (int i = 0; i + 1 < buf.length(); i++) {
                out.add(buf.substring(i, i + 2));
            }
        }
        buf.setLength(0);
    }

    // ---------------------------------------------------------------- 任务效能
    /** 期望里的"要点"被输出覆盖的比例（召回）。 */
    public static double coverage(String output, String expected) {
        Set<String> exp = new LinkedHashSet<>(tokens(expected));
        if (exp.isEmpty()) {
            return 1.0;
        }
        Set<String> got = new LinkedHashSet<>(tokens(output));
        long hit = exp.stream().filter(got::contains).count();
        return (double) hit / exp.size();
    }

    /** 输出里"有用内容"占的比例（精确率；超出期望的部分算噪音）。 */
    public static double precision(String output, String expected) {
        Set<String> got = new LinkedHashSet<>(tokens(output));
        if (got.isEmpty()) {
            return 0.0;
        }
        Set<String> exp = new LinkedHashSet<>(tokens(expected));
        long hit = got.stream().filter(exp::contains).count();
        return (double) hit / got.size();
    }

    /** 覆盖率与精确率的调和平均。没有期望输出时退化为 1（无标准答案就不惩罚）。 */
    public static double f1(String output, String expected) {
        if (expected == null || expected.isBlank()) {
            return output == null || output.isBlank() ? 0.0 : 1.0;
        }
        double p = precision(output, expected);
        double r = coverage(output, expected);
        return p + r == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    /** 完整度：输出长度 / 期望长度，截断到 [0,1]（太短扣分，太长不额外加分）。 */
    public static double completeness(String output, String expected) {
        int expLen = tokens(expected).size();
        int outLen = tokens(output).size();
        if (expLen == 0) {
            return outLen > 0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, (double) outLen / expLen);
    }

    /** 任务完成：覆盖率与 f1 都过线才算完成（二值指标，用于"成功率"统计）。 */
    public static double taskSuccess(String output, String expected) {
        if (expected == null || expected.isBlank()) {
            return output == null || output.isBlank() ? 0.0 : 1.0;
        }
        return (coverage(output, expected) >= 0.5 && f1(output, expected) >= 0.4) ? 1.0 : 0.0;
    }

    /** 一致性：两次输出之间的相似度（用于"同输入重复跑"的稳定性）。 */
    public static double consistency(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.isBlank() && b.isBlank()) {
            return 1.0;
        }
        double p = precision(b, a);
        double r = coverage(b, a);
        return p + r == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    // ---------------------------------------------------------------- 描述统计
    public record Summary(int n, double min, double max, double mean, double std, double median) {
    }

    public static Summary summary(List<Double> values) {
        List<Double> v = values.stream().filter(x -> x != null && !x.isNaN()).sorted().toList();
        if (v.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        double sum = v.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / v.size();
        double var = v.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / Math.max(1, v.size() - 1);
        double median = v.size() % 2 == 1
                ? v.get(v.size() / 2)
                : (v.get(v.size() / 2 - 1) + v.get(v.size() / 2)) / 2.0;
        return new Summary(v.size(), v.get(0), v.get(v.size() - 1), mean, Math.sqrt(var), median);
    }

    /** 直方图（{@code buckets} 个等宽桶）。 */
    public static Map<String, Integer> histogram(List<Double> values, int buckets) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (values.isEmpty()) {
            return out;
        }
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double span = Math.max(1e-9, max - min);
        int n = Math.max(1, buckets);
        for (int i = 0; i < n; i++) {
            double lo = min + span * i / n;
            double hi = min + span * (i + 1) / n;
            out.put(String.format(Locale.ROOT, "%.2f–%.2f", lo, hi), 0);
        }
        List<String> keys = new ArrayList<>(out.keySet());
        for (double v : values) {
            int idx = (int) Math.floor((v - min) / span * n);
            idx = Math.max(0, Math.min(n - 1, idx));
            out.merge(keys.get(idx), 1, Integer::sum);
        }
        return out;
    }

    // ---------------------------------------------------------------- 最优化
    /**
     * TOPSIS 多准则排序（越接近正理想解越好）。
     *
     * @param alternatives 每行是一个方案（如某个 Skill），列顺序与 {@code weights} 对齐
     * @param weights      每个准则的权重（全为正）
     * @param higherBetter 每个准则的方向（true = 越大越好）
     * @return 每行一个得分，越接近 1 越好
     */
    public static double[] topsis(double[][] alternatives, double[] weights, boolean[] higherBetter) {
        int rows = alternatives.length;
        if (rows == 0) {
            return new double[0];
        }
        int cols = alternatives[0].length;
        double[][] norm = new double[rows][cols];
        for (int c = 0; c < cols; c++) {
            double sq = 0;
            for (int r = 0; r < rows; r++) {
                sq += alternatives[r][c] * alternatives[r][c];
            }
            double denom = Math.sqrt(sq);
            for (int r = 0; r < rows; r++) {
                norm[r][c] = denom == 0 ? 0 : alternatives[r][c] / denom * weights[c];
            }
        }
        double[] best = new double[cols];
        double[] worst = new double[cols];
        for (int c = 0; c < cols; c++) {
            double mx = Double.NEGATIVE_INFINITY;
            double mn = Double.POSITIVE_INFINITY;
            for (int r = 0; r < rows; r++) {
                mx = Math.max(mx, norm[r][c]);
                mn = Math.min(mn, norm[r][c]);
            }
            best[c] = higherBetter[c] ? mx : mn;
            worst[c] = higherBetter[c] ? mn : mx;
        }
        double[] out = new double[rows];
        for (int r = 0; r < rows; r++) {
            double dBest = 0;
            double dWorst = 0;
            for (int c = 0; c < cols; c++) {
                dBest += Math.pow(norm[r][c] - best[c], 2);
                dWorst += Math.pow(norm[r][c] - worst[c], 2);
            }
            dBest = Math.sqrt(dBest);
            dWorst = Math.sqrt(dWorst);
            out[r] = dBest + dWorst == 0 ? 0 : dWorst / (dBest + dWorst);
        }
        return out;
    }

    /** z-score 归一化（缺失值按均值补齐）。 */
    public static double[] zscore(double[] values) {
        if (values.length == 0) {
            return values;
        }
        double mean = 0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.length;
        double var = 0;
        for (double v : values) {
            var += (v - mean) * (v - mean);
        }
        double std = Math.sqrt(var / Math.max(1, values.length - 1));
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = std == 0 ? 0 : (values[i] - mean) / std;
        }
        return out;
    }
}
