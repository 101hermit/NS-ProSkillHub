package org.skillhub.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.dto.EvalDtos;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.model.Skill;
import org.skillhub.port.AgentPort;
import org.skillhub.port.IdPort;
import org.skillhub.port.McpPort;
import org.skillhub.support.CardFactory;
import org.skillhub.support.Dates;
import org.skillhub.support.Metrics;
import org.skillhub.support.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单点实测与"简单质量评估"。
 *
 * <p><b>能力边界（docs/00 §2.2）</b>：只用统计学与最优化 —— 加权评分、z-score、TOPSIS。
 * 不涉及知识图谱、嵌入向量、聚类、假设检验（那是专业版）。
 *
 * <p>一次实测的完整链路：
 * <pre>
 *   选 Skill → 组装系统提示（手册 + 场景 + 可选 MCP 上下文）
 *            → 调 Agent（真实模型，或离线桩兜底）
 *            → 用 Metrics 打分（任务效能 + 通信性能）
 *            → 落 eval_run + eval_score，回填 skill.eval_count / avg_score
 * </pre>
 *
 * <p><b>为什么要"推理过程"</b>：只看输出无法判断"它是真的按手册做的，还是碰巧答对了"。
 * 推理过程是 Skill 是否被真正调用的证据，因此单独存一列并单独展示。
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    /** 指标口径（同时用于落库与页面展示，保证"页面上写的"和"算的时候用的"是同一份）。 */
    private static final List<MetricSpec> SPECS = List.of(
            new MetricSpec("correctness", "正确性", "EFFICACY", "分", true, 3.0,
                    "F1 = 2·精确率·召回率/(精确率+召回率)，按词/字二元组比对"),
            new MetricSpec("coverage", "期望覆盖", "EFFICACY", "比例", true, 2.0,
                    "期望输出里的要点被输出覆盖的比例"),
            new MetricSpec("completeness", "内容完整", "EFFICACY", "比例", true, 1.0,
                    "输出长度 / 期望长度，上限 1"),
            new MetricSpec("task_success", "任务完成", "EFFICACY", "0/1", true, 2.0,
                    "覆盖率 ≥0.5 且 F1 ≥0.4 记 1，否则 0"),
            new MetricSpec("consistency", "一致性", "EFFICACY", "比例", true, 1.0,
                    "同输入重复跑的相似度；单次运行记 1"),
            new MetricSpec("ttft", "首字效率", "COMMUNICATION", "比例", true, 1.0,
                    "1 − min(1, 首字时延/2000ms)"),
            new MetricSpec("latency", "时延效率", "COMMUNICATION", "比例", true, 1.0,
                    "1 − min(1, 总时延/30000ms)"),
            new MetricSpec("throughput", "吞吐", "COMMUNICATION", "tokens/s 归一", true, 1.0,
                    "completion_tokens / 时延，按 50 tokens/s 归一"),
            new MetricSpec("success", "成功率", "COMMUNICATION", "0/1", true, 1.0,
                    "这次调用是否成功（1/0）"));

    /** 一个指标的定义。 */
    public record MetricSpec(String code, String label, String dimension, String unit, boolean higherBetter,
                             double weight, String formula) {
    }

    private final EvalMapper evalMapper;
    private final SkillMapper skillMapper;
    private final SkillService skillService;
    private final AgentPort agentPort;
    private final McpPort mcpPort;
    private final CardFactory cardFactory;
    private final IdPort idPort;

    public EvalService(EvalMapper evalMapper, SkillMapper skillMapper, SkillService skillService,
                       AgentPort agentPort, McpPort mcpPort, CardFactory cardFactory, IdPort idPort) {
        this.evalMapper = evalMapper;
        this.skillMapper = skillMapper;
        this.skillService = skillService;
        this.agentPort = agentPort;
        this.mcpPort = mcpPort;
        this.cardFactory = cardFactory;
        this.idPort = idPort;
    }

    // ================================================================ 单点实测

    @Transactional
    public EvalDtos.RunItem run(EvalDtos.RunRequest request) {
        if (request.getInputText() == null || request.getInputText().isBlank()) {
            throw ApiException.ofMessage(ErrorCode.MISSING_PARAM,
                    "缺少问题输入。怎么办：写一个你想让智能体回答的问题。");
        }
        Skill skill = skillService.require(request.getSkillId());

        String prompt = buildSystemPrompt(skill, request.getSceneCode());
        StringBuilder toolContext = new StringBuilder();
        boolean offline = false;
        String toolError = "";
        if (request.isUseMcp()) {
            McpPort.Result mcp = mcpPort.call(new McpPort.Call(
                    request.getMcpCode(), null, Map.of("query", request.getInputText())));
            offline |= mcp.offline();
            if (mcp.ok()) {
                toolContext.append(mcp.content());
            } else {
                toolError = mcp.error();
            }
        }
        String userInput = toolContext.length() == 0
                ? request.getInputText()
                : request.getInputText() + "\n\n【工具返回的参考信息】\n" + toolContext;

        Map<String, Double> params = new LinkedHashMap<>();
        if (request.getTemperature() != null) {
            params.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            params.put("top_p", request.getTopP());
        }
        int maxTokens = request.getMaxTokens() == null ? 2048 : request.getMaxTokens();

        AgentPort.Reply reply = agentPort.invoke(
                new AgentPort.Request(request.getAgentCode(), prompt, userInput, params, maxTokens));
        offline |= reply.offline();

        MsEntities.EvalRun row = new MsEntities.EvalRun();
        row.code = idPort.code("run");
        row.skillId = skill.skillId;
        row.agentCode = request.getAgentCode();
        row.mcpCode = request.isUseMcp() ? request.getMcpCode() : "";
        row.paramsJson = toJson(params);
        row.sceneCode = Texts.firstNonBlank(request.getSceneCode(), "general");
        row.inputText = request.getInputText();
        row.expectedText = request.getExpectedText() == null ? "" : request.getExpectedText();
        row.outputText = reply.output();
        row.reasoningText = reply.reasoning();
        row.status = reply.ok() ? "DONE" : "FAILED";
        row.latencyMs = reply.latencyMs();
        row.ttftMs = reply.ttftMs();
        row.promptTokens = reply.promptTokens();
        row.completionTokens = reply.completionTokens();
        row.error = Texts.truncate(nz(reply.error(), toolError), 1000);
        // score_total 是 NOT NULL 列：先给 0，算完分再回写（同一次请求内完成）
        row.scoreTotal = BigDecimal.ZERO;
        evalMapper.insertRun(row);

        List<MsEntities.EvalScore> scores = score(row, reply);
        if (!scores.isEmpty()) {
            rows(scores).forEach(s -> s.runId = row.id);
            evalMapper.insertScores(scores);
        }
        row.scoreTotal = BigDecimal.valueOf(weightedTotal(scores)).setScale(2, RoundingMode.HALF_UP);
        row.scoreJson = toJson(scoresAsMap(scores));
        evalMapper.finishRun(row);
        refreshSkillAggregate(skill.skillId);

        return toItem(row, scores, skill, request.getAgentCode());
    }

    /** 打分：把一次调用的客观事实映射成指标值。 */
    private List<MsEntities.EvalScore> score(MsEntities.EvalRun row, AgentPort.Reply reply) {
        List<MsEntities.EvalScore> out = new ArrayList<>();
        String expected = row.expectedText == null ? "" : row.expectedText;
        String output = nz(row.outputText, "");
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("correctness", Metrics.f1(output, expected));
        values.put("coverage", Metrics.coverage(output, expected));
        values.put("completeness", Metrics.completeness(output, expected));
        values.put("task_success", Metrics.taskSuccess(output, expected));
        values.put("consistency", reply.ok() ? 1.0 : 0.0);
        values.put("ttft", 1.0 - Math.min(1.0, row.ttftMs / 2000.0));
        values.put("latency", 1.0 - Math.min(1.0, row.latencyMs / 30000.0));
        double tps = row.latencyMs <= 0 ? 0 : row.completionTokens * 1000.0 / row.latencyMs;
        values.put("throughput", Math.min(1.0, tps / 50.0));
        values.put("success", reply.ok() ? 1.0 : 0.0);

        for (MetricSpec spec : SPECS) {
            MsEntities.EvalScore s = new MsEntities.EvalScore();
            s.metricCode = spec.code();
            s.metricValue = round(values.getOrDefault(spec.code(), 0.0));
            s.weight = spec.weight();
            s.detail = detailOf(spec, values.getOrDefault(spec.code(), 0.0));
            out.add(s);
        }
        return out;
    }

    private static String detailOf(MetricSpec spec, double value) {
        return switch (spec.code()) {
            case "correctness" -> "F1 = " + pct(value);
            case "coverage" -> "期望要点覆盖 " + pct(value);
            case "completeness" -> "内容完整度 " + pct(value);
            case "task_success" -> value >= 0.5 ? "任务完成" : "任务未完成";
            case "consistency" -> value >= 1 ? "单次运行，记满分" : "本次调用失败，一致性记 0";
            case "ttft" -> "首字效率 " + pct(value);
            case "latency" -> "时延效率 " + pct(value);
            case "throughput" -> "吞吐 " + pct(value);
            case "success" -> value >= 0.5 ? "调用成功" : "调用失败";
            default -> pct(value);
        };
    }

    private static double weightedTotal(List<MsEntities.EvalScore> scores) {
        double num = 0;
        double den = 0;
        for (MsEntities.EvalScore s : scores) {
            num += s.metricValue * s.weight;
            den += s.weight;
        }
        return den == 0 ? 0 : num / den * 100.0;
    }

    private void refreshSkillAggregate(String skillId) {
        List<MsEntities.EvalRun> runs = evalMapper.runsOfSkill(skillId);
        if (runs.isEmpty()) {
            return;
        }
        double sum = runs.stream().mapToDouble(r -> r.scoreTotal == null ? 0 : r.scoreTotal.doubleValue()).sum();
        Skill patch = new Skill();
        patch.skillId = skillId;
        skillMapper.updateEvalAggregate(skillId,
                BigDecimal.valueOf(sum / runs.size()).setScale(2, RoundingMode.HALF_UP));
    }

    // ================================================================ 读

    public PageResult<EvalDtos.RunItem> page(PageQuery query, String skillId, String status) {
        String sid = blankToNull(skillId);
        long total = evalMapper.countRuns(sid, blankToNull(status));
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<EvalDtos.RunItem> items = evalMapper.listRuns(sid, blankToNull(status),
                        query.getLimit(), query.getOffset()).stream()
                .map(r -> toItem(r, evalMapper.findScores(r.id), skillService.find(r.skillId), r.agentCode))
                .toList();
        return PageResult.of(query, total, items);
    }

    public EvalDtos.RunItem detail(String code) {
        MsEntities.EvalRun row = evalMapper.findRun(code);
        if (row == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "实测记录「" + code + "」");
        }
        return toItem(row, evalMapper.findScores(row.id), skillService.find(row.skillId), row.agentCode);
    }

    public List<EvalDtos.MetricItem> metrics() {
        return SPECS.stream().map(s -> new EvalDtos.MetricItem(s.code(), s.label(), s.dimension(),
                dimensionLabel(s.dimension()), s.unit(), s.higherBetter(), s.weight(), s.formula(),
                null)).toList();
    }

    /** 某个 Skill 的质量评估汇总（含指标均值与 TOPSIS 排名）。 */
    public EvalDtos.Summary summary(String skillId) {
        Skill skill = skillService.require(skillId);
        List<MsEntities.EvalRun> runs = evalMapper.runsOfSkill(skillId);
        if (runs.isEmpty()) {
            return new EvalDtos.Summary(skillId, 0, 0, 0, 0, "", "",
                    List.of(), List.of(), "这个 Skill 还没有实测记录。到「在线实测」跑一次，这里就会有数据。");
        }
        double avg = runs.stream().mapToDouble(r -> dbl(r.scoreTotal)).average().orElse(0);
        MsEntities.EvalRun best = runs.stream().max((a, b) -> Double.compare(dbl(a.scoreTotal), dbl(b.scoreTotal))).orElse(runs.get(0));
        MsEntities.EvalRun worst = runs.stream().min((a, b) -> Double.compare(dbl(a.scoreTotal), dbl(b.scoreTotal))).orElse(runs.get(0));

        // 指标均值
        Map<String, double[]> acc = new LinkedHashMap<>();
        for (MsEntities.EvalRun r : runs) {
            for (MsEntities.EvalScore s : evalMapper.findScores(r.id)) {
                double[] a = acc.computeIfAbsent(s.metricCode, k -> new double[2]);
                a[0] += s.metricValue;
                a[1] += 1;
            }
        }
        List<EvalDtos.MetricAvg> averages = new ArrayList<>();
        for (MetricSpec spec : SPECS) {
            double[] a = acc.get(spec.code());
            if (a == null || a[1] == 0) {
                continue;
            }
            averages.add(new EvalDtos.MetricAvg(spec.code(), spec.label(), spec.dimension(),
                    round(a[0] / a[1]), (int) a[1]));
        }

        List<Map<String, Object>> ranking = ranking();
        String conclusion = String.format(java.util.Locale.ROOT,
                "该 Skill 共实测 %d 次，平均综合分 %.1f（最好 %.1f / 最差 %.1f）。"
                        + "任务效能类指标权重更高（正确性 3 / 覆盖 2 / 完成 2 / 完整 1 / 一致 1），通信性能类合计 4。",
                runs.size(), avg, dbl(best.scoreTotal), dbl(worst.scoreTotal));
        return new EvalDtos.Summary(skillId, runs.size(), round(avg), round(dbl(best.scoreTotal)),
                round(dbl(worst.scoreTotal)), best.code, worst.code, averages, ranking, conclusion);
    }

    /** 所有"有实测记录"的 Skill 的综合排名（TOPSIS）。 */
    public List<Map<String, Object>> ranking() {
        List<Skill> skills = skillMapper.listAllCards().stream()
                .filter(s -> s.evalCount != null && s.evalCount > 0)
                .limit(30)
                .toList();
        if (skills.size() < 2) {
            return skills.stream().map(s -> Map.<String, Object>of(
                    "skill_id", s.skillId, "name", s.name, "avg_score", dbl(s.avgScore),
                    "eval_count", s.evalCount, "rank", 1, "topsis", 1.0)).toList();
        }
        boolean[] dirs = {true, true};
        double[] weights = {3.0, 1.0};
        double[][] matrix = new double[skills.size()][2];
        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            matrix[i][0] = dbl(s.avgScore);
            matrix[i][1] = Math.min(1.0, (s.evalCount == null ? 0 : s.evalCount) / 10.0);
        }
        double[] scores = Metrics.topsis(matrix, weights, dirs);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skill_id", s.skillId);
            row.put("name", s.name);
            row.put("avg_score", dbl(s.avgScore));
            row.put("eval_count", s.evalCount);
            row.put("topsis", round(scores[i]));
            out.add(row);
        }
        out.sort((a, b) -> Double.compare((double) b.get("topsis"), (double) a.get("topsis")));
        for (int i = 0; i < out.size(); i++) {
            out.get(i).put("rank", i + 1);
        }
        return out;
    }

    // ================================================================ 工具

    private String buildSystemPrompt(Skill skill, String scene) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个被 Skill 赋能的工作助手。请严格按下面这份手册做事，并在回答前先说明你的步骤。\n\n");
        sb.append("【Skill 名称】").append(skill.name).append('\n');
        sb.append("【Skill 说明】").append(nz(skill.description, "（无）")).append('\n');
        sb.append("【行业场景】").append(CardFactory.sceneLabel(Texts.firstNonBlank(scene, skill.sceneCode)))
                .append('\n');
        String content = skill.content == null ? "" : skill.content;
        sb.append("【Skill 手册】\n").append(Texts.truncate(content, 6000)).append('\n');
        sb.append("【输出要求】先用 3-5 步说明你的做法，再给最终答复。答复要具体、可执行。\n");
        return sb.toString();
    }

    private EvalDtos.RunItem toItem(MsEntities.EvalRun r, List<MsEntities.EvalScore> scores, Skill skill,
                                    String agentCode) {
        List<EvalDtos.ScoreItem> items = scores == null ? List.of() : scores.stream()
                .map(s -> {
                    MetricSpec spec = SPECS.stream().filter(x -> x.code().equals(s.metricCode))
                            .findFirst().orElse(null);
                    return new EvalDtos.ScoreItem(s.metricCode,
                            spec == null ? s.metricCode : spec.label(),
                            spec == null ? "" : spec.dimension(),
                            spec == null ? "" : dimensionLabel(spec.dimension()),
                            s.metricValue == null ? 0 : s.metricValue,
                            display(s.metricCode, s.metricValue == null ? 0 : s.metricValue),
                            s.weight == null ? 1 : s.weight, s.detail);
                }).toList();
        return new EvalDtos.RunItem(r.code, r.skillId, skill == null ? r.skillId : skill.name,
                agentCode, agentCode, r.mcpCode, r.sceneCode, r.status,
                r.inputText, r.expectedText, r.outputText, r.reasoningText,
                r.latencyMs == null ? 0 : r.latencyMs, r.ttftMs == null ? 0 : r.ttftMs,
                r.promptTokens == null ? 0 : r.promptTokens,
                r.completionTokens == null ? 0 : r.completionTokens,
                dbl(r.scoreTotal), r.error != null && !r.error.isBlank(),
                r.error == null ? "" : r.error,
                Dates.format(r.createdAt), Dates.format(r.finishedAt), items);
    }

    private static String display(String code, double v) {
        return switch (code) {
            case "task_success", "success" -> v >= 0.5 ? "是" : "否";
            default -> pct(v);
        };
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", v * 100);
    }

    private static String dimensionLabel(String dimension) {
        return "EFFICACY".equals(dimension) ? "任务效能" : "通信性能";
    }

    private static List<MsEntities.EvalScore> rows(List<MsEntities.EvalScore> scores) {
        return scores;
    }

    private static Map<String, Object> scoresAsMap(List<MsEntities.EvalScore> scores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (MsEntities.EvalScore s : scores) {
            m.put(s.metricCode, s.metricValue);
        }
        return m;
    }

    private static double dbl(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String nz(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
