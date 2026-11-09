package org.skillhub.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.ProDtos;
import org.skillhub.mapper.ProMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.ProEntities;
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
 * 多维实测（实验矩阵）。三种模式对应需求里的 (a)(b)(c)：
 *
 * <ul>
 *   <li><b>(a) 单体实测</b> → 用标准版的 {@code /eval/runs}（一个 Skill × 一个 Agent × 一组参数）</li>
 *   <li><b>(b) Skill 维度扫描</b> → {@code SKILL_SCAN}：一个 Skill × 多 Agent × 多参数网格 × 多用例</li>
 *   <li><b>(c) Skill 横向对比</b> → {@code SKILL_COMPARE}：多 Skill × 固定条件（单 Agent / 单参数 / 单场景）</li>
 * </ul>
 *
 * <p>执行是**串行**的（内网模型并发受限，串行更容易解释、也更容易复现）。
 * 每个单元保存输出、推理过程、时延、tokens 与指标分，便于事后做统计与可视化。
 */
@Service
public class MatrixService {

    private static final Logger log = LoggerFactory.getLogger(MatrixService.class);
    private static final int MAX_CELLS = 120;

    private final ProMapper proMapper;
    private final SkillMapper skillMapper;
    private final AgentPort agentPort;
    private final McpPort mcpPort;
    private final IdPort idPort;

    public MatrixService(ProMapper proMapper, SkillMapper skillMapper, AgentPort agentPort,
                         McpPort mcpPort, IdPort idPort) {
        this.proMapper = proMapper;
        this.skillMapper = skillMapper;
        this.agentPort = agentPort;
        this.mcpPort = mcpPort;
        this.idPort = idPort;
    }

    // ================================================================ 创建

    @Transactional
    public ProDtos.MatrixItem create(ProDtos.MatrixRequest req) {
        String type = "SKILL_COMPARE".equalsIgnoreCase(req.getMatrixType()) ? "SKILL_COMPARE" : "SKILL_SCAN";
        List<String> skills = req.getSkillIds().isEmpty()
                ? List.of(pickDefaultSkill()) : req.getSkillIds();
        List<String> agents = req.getAgentCodes().isEmpty() ? List.of("local-stub") : req.getAgentCodes();
        List<String> mcps = req.getMcpCodes().isEmpty() ? List.of("builtin-calc") : req.getMcpCodes();
        Map<String, List<Double>> grid = normalizeGrid(req.getParamGrid());
        List<ProDtos.Case> cases = req.getCases().isEmpty() ? defaultCases(req.getSceneCode()) : req.getCases();

        // (c) 横向对比：固定条件 —— 单 Agent、单参数、单场景
        if ("SKILL_COMPARE".equals(type)) {
            agents = agents.subList(0, 1);
            grid = singleValue(grid);
        }

        List<Map<String, Double>> paramSets = expandGrid(grid);
        int planned = skills.size() * agents.size() * mcps.size() * paramSets.size() * cases.size();
        if (planned > MAX_CELLS) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM,
                    "这次实验会产生 " + planned + " 个单元，超过上限 " + MAX_CELLS
                            + "。怎么办：减少 Agent/MCP/参数/用例的数量，或拆成几次实验。");
        }

        ProEntities.Matrix m = new ProEntities.Matrix();
        m.code = idPort.code("mx");
        m.name = Texts.firstNonBlank(req.getName(), type + " 实验");
        m.matrixType = type;
        m.skillIds = String.join(",", skills);
        m.agentCodes = String.join(",", agents);
        m.mcpCodes = String.join(",", mcps);
        m.paramGrid = toJson(grid);
        m.sceneCode = Texts.firstNonBlank(req.getSceneCode(), "general");
        m.caseJson = toJson(cases);
        m.status = "PENDING";
        m.plannedCells = planned;
        m.doneCells = 0;
        m.summaryJson = "{}";
        proMapper.insertMatrix(m);

        int caseIdx = 0;
        for (ProDtos.Case c : cases) {
            caseIdx++;
            for (String skillId : skills) {
                for (String agent : agents) {
                    for (String mcp : mcps) {
                        for (Map<String, Double> params : paramSets) {
                            ProEntities.Cell cell = new ProEntities.Cell();
                            cell.matrixCode = m.code;
                            cell.skillId = skillId;
                            cell.agentCode = agent;
                            cell.mcpCode = mcp;
                            cell.paramsJson = toJson(params);
                            cell.sceneCode = m.sceneCode;
                            cell.caseCode = Texts.firstNonBlank(c.code(), "case-" + caseIdx);
                            cell.inputText = c.input();
                            cell.expectedText = c.expected() == null ? "" : c.expected();
                            proMapper.insertCell(cell);
                        }
                    }
                }
            }
        }
        return toItem(m);
    }

    // ================================================================ 执行

    /** 执行整个矩阵（逐格串行）。返回执行后的概要。 */
    @Transactional
    public ProDtos.MatrixDetail run(String code) {
        ProEntities.Matrix m = require(code);
        List<ProEntities.Cell> cells = proMapper.cellsOf(code);
        int done = 0;
        for (ProEntities.Cell cell : cells) {
            if ("DONE".equals(cell.status)) {
                done++;
                continue;
            }
            executeCell(cell);
            if ("DONE".equals(cell.status)) {
                done++;
            }
        }
        m.doneCells = done;
        m.status = done == cells.size() ? "DONE" : (done > 0 ? "PARTIAL" : "FAILED");
        m.summaryJson = toJson(summarize(cells));
        proMapper.updateMatrix(m);
        log.info("矩阵 {} 执行完成：{}/{}", code, done, cells.size());
        return detail(code);
    }

    private void executeCell(ProEntities.Cell cell) {
        Skill skill = skillMapper.findDetailBySkillId(cell.skillId);
        if (skill == null) {
            cell.status = "FAILED";
            cell.error = "Skill 不存在：" + cell.skillId;
            cell.scoreTotal = BigDecimal.ZERO;
            proMapper.finishCell(cell);
            return;
        }
        String prompt = buildPrompt(skill, cell.sceneCode);
        String user = cell.inputText;
        boolean useMcp = cell.mcpCode != null && !cell.mcpCode.isBlank();
        if (useMcp) {
            McpPort.Result mcp = mcpPort.call(new McpPort.Call(cell.mcpCode, null,
                    Map.of("query", cell.inputText)));
            if (mcp.ok()) {
                user = user + "\n\n【工具返回的参考信息】\n" + mcp.content();
            }
        }
        Map<String, Double> params = parseParams(cell.paramsJson);
        int maxTokens = params.containsKey("max_tokens") ? params.get("max_tokens").intValue() : 1024;
        AgentPort.Reply reply = agentPort.invoke(new AgentPort.Request(
                cell.agentCode, prompt, user, params, maxTokens));

        cell.outputText = reply.output();
        cell.reasoningText = reply.reasoning();
        cell.status = reply.ok() ? "DONE" : "FAILED";
        cell.latencyMs = reply.latencyMs();
        cell.ttftMs = reply.ttftMs();
        cell.promptTokens = reply.promptTokens();
        cell.completionTokens = reply.completionTokens();
        cell.error = Texts.truncate(reply.error() == null ? "" : reply.error(), 1000);
        cell.scoreTotal = BigDecimal.valueOf(score(reply, cell.expectedText))
                .setScale(2, RoundingMode.HALF_UP);
        cell.scoreJson = toJson(scoreDetail(reply, cell.expectedText));
        proMapper.finishCell(cell);
    }

    /** 单元打分：与标准版同一套口径（正确性/覆盖/完整/完成/一致 + 时延/首字/吞吐/成功）。 */
    private static double score(AgentPort.Reply reply, String expected) {
        String out = reply.output() == null ? "" : reply.output();
        String exp = expected == null ? "" : expected;
        double[] values = {
                Metrics.f1(out, exp),
                Metrics.coverage(out, exp),
                Metrics.completeness(out, exp),
                Metrics.taskSuccess(out, exp),
                reply.ok() ? 1.0 : 0.0,
                1.0 - Math.min(1.0, reply.ttftMs() / 2000.0),
                1.0 - Math.min(1.0, reply.latencyMs() / 30000.0),
                reply.latencyMs() <= 0 ? 0 : Math.min(1.0, reply.completionTokens() * 1000.0 / reply.latencyMs() / 50.0),
                reply.ok() ? 1.0 : 0.0,
        };
        double[] weights = {3, 2, 1, 2, 1, 1, 1, 1, 1};
        double num = 0;
        double den = 0;
        for (int i = 0; i < values.length; i++) {
            num += values[i] * weights[i];
            den += weights[i];
        }
        return den == 0 ? 0 : num / den * 100.0;
    }

    private static Map<String, Object> scoreDetail(AgentPort.Reply reply, String expected) {
        String out = reply.output() == null ? "" : reply.output();
        String exp = expected == null ? "" : expected;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("correctness", Metrics.f1(out, exp));
        m.put("coverage", Metrics.coverage(out, exp));
        m.put("completeness", Metrics.completeness(out, exp));
        m.put("task_success", Metrics.taskSuccess(out, exp));
        m.put("ttft_ms", reply.ttftMs());
        m.put("latency_ms", reply.latencyMs());
        m.put("completion_tokens", reply.completionTokens());
        return m;
    }

    // ================================================================ 读

    public List<ProDtos.MatrixItem> list() {
        return proMapper.listMatrices().stream().map(MatrixService::toItem).toList();
    }

    /**
     * 把一次矩阵实验导成一份 Markdown 报告（可直接贴进周报 / 评审 / 归档）。
     *
     * <p>为什么是 Markdown 而不是 PDF/Excel：Markdown 是纯文本 —— 能进 Git、能 diff、
     * 能被任何文档系统打开，而且不需要为了"导出"引入一个几千行的报表库。
     * 一个交付物的价值在于"别人愿不愿意读"，不在于格式多花哨。
     *
     * <p>内容顺序按评审时的关注点排：先结论（谁最好、差多少），再明细（每个单元一行），
     * 最后才是失败清单 —— 失败明细放在最后并不是不重要，而是评审时要先看"能不能用"，
     * 再看"哪里不能用"。
     */
    public String markdown(String code) {
        ProDtos.MatrixDetail detail = detail(code);
        ProDtos.MatrixItem m = detail.matrix();
        List<ProDtos.CellItem> cells = detail.cells();

        long done = cells.stream().filter(c -> "DONE".equals(c.status())).count();
        long failed = cells.stream()
                .filter(c -> "FAILED".equals(c.status()) || "ERROR".equals(c.status())).count();
        long pending = cells.size() - done - failed;
        double avgScore = done == 0 ? 0
                : round(cells.stream().filter(c -> "DONE".equals(c.status()))
                        .mapToDouble(ProDtos.CellItem::scoreTotal).average().orElse(0));
        int avgLatency = (int) Math.round(cells.stream().filter(c -> "DONE".equals(c.status()))
                .mapToInt(ProDtos.CellItem::latencyMs).average().orElse(0));

        StringBuilder sb = new StringBuilder();
        sb.append("# 实验报告 · ").append(m.name()).append('\n').append('\n');
        sb.append("> 由 ProSkillHub 自动生成，数据来自本次实验的实际执行结果。\n").append('\n');

        sb.append("## 一、实验概况\n").append('\n');
        sb.append("| 项 | 值 |\n|---|---|\n");
        sb.append("| 实验编号 | `").append(m.code()).append("` |\n");
        sb.append("| 实验类型 | ").append(nzText(m.matrixType())).append(" |\n");
        sb.append("| 场景 | ").append(nzText(m.sceneCode())).append(" |\n");
        sb.append("| 状态 | ").append(nzText(m.status())).append(" |\n");
        sb.append("| 单元 | 计划 ").append(m.plannedCells()).append(" · 完成 ").append(done)
                .append(" · 失败 ").append(failed).append(" · 未跑 ").append(pending).append(" |\n");
        sb.append("| 平均分 | ").append(done == 0 ? "—（还没有完成的单元）" : avgScore + " / 100").append(" |\n");
        sb.append("| 平均时延 | ").append(done == 0 ? "—" : avgLatency + " ms").append(" |\n");
        sb.append("| 创建 / 完成 | ").append(nzText(m.createdAt())).append(" / ")
                .append(nzText(m.finishedAt())).append(" |\n");
        sb.append('\n');

        sb.append("## 二、结论（自动归纳）\n").append('\n');
        try {
            ProDtos.Compare cmp = compare(code);
            sb.append(cmp.conclusion()).append('\n').append('\n');
            appendRankTable(sb, "最佳 Skill", cmp.bySkill(), "skill");
            appendRankTable(sb, "最佳 Agent", cmp.byAgent(), "agent");
            if (cmp.byParams() != null && !cmp.byParams().isEmpty()) {
                appendRankTable(sb, "参数组合", cmp.byParams(), "params");
            }
        } catch (RuntimeException e) {
            sb.append("（本次实验还没有可对比的完成单元，结论待补。）\n\n");
        }

        sb.append("## 三、单元明细\n").append('\n');
        if (cells.isEmpty()) {
            sb.append("（没有单元）\n\n");
        } else {
            sb.append("| # | Skill | Agent | MCP | 用例 | 状态 | 分数 | 时延 | 备注 |\n");
            sb.append("|---|---|---|---|---|---|---|---|---|\n");
            int i = 0;
            for (ProDtos.CellItem c : cells) {
                i++;
                sb.append("| ").append(i)
                        .append(" | ").append(md(nzText(c.skillName())))
                        .append(" | ").append(md(c.agentCode()))
                        .append(" | ").append(md(nzText(c.mcpCode())))
                        .append(" | ").append(md(nzText(c.caseCode())))
                        .append(" | ").append(c.status())
                        .append(" | ").append("DONE".equals(c.status()) ? c.scoreTotal() : "—")
                        .append(" | ").append(c.latencyMs() > 0 ? c.latencyMs() + "ms" : "—")
                        .append(" | ").append(md(shorten(c.error(), 40)))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        List<ProDtos.CellItem> bad = cells.stream()
                .filter(c -> !"DONE".equals(c.status())).toList();
        if (!bad.isEmpty()) {
            sb.append("## 四、未通过 / 未运行的单元\n").append('\n');
            for (ProDtos.CellItem c : bad) {
                sb.append("- **").append(c.status()).append("** `").append(c.agentCode()).append("` × `")
                        .append(nzText(c.mcpCode())).append("`（用例 ").append(nzText(c.caseCode()))
                        .append("）：").append(nzText(c.error())).append('\n');
            }
            sb.append('\n');
        }

        sb.append("---\n").append('\n');
        sb.append("评分口径：0-100，越高越好；每一分都由指标字典里的指标算出，可在「实测」页展开看构成。\n");
        sb.append("复现方式：同一个实验编号下点「执行」即可复跑（参数、用例、目标都在库里，不依赖本机状态）。\n");
        return sb.toString();
    }

    private static void appendRankTable(StringBuilder sb, String title,
                                        List<Map<String, Object>> rows, String keyField) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        sb.append("### ").append(title).append('\n').append('\n');
        sb.append("| 排名 | 对象 | 均分 | 成功率 | 时延效率 | 样本 |\n|---|---|---|---|---|---|\n");
        int rank = 0;
        for (Map<String, Object> r : rows) {
            rank++;
            // aggregate() 里的 "key" 是原始编号；Skill 分组还额外带一个 "name"（人读的名字）
            Object raw = r.getOrDefault("name", r.getOrDefault("key", "-"));
            sb.append("| ").append(rank)
                    .append(" | ").append(md(String.valueOf(raw)))
                    .append(" | ").append(r.getOrDefault("avg_score", "-"))
                    .append(" | ").append(r.getOrDefault("success_rate", "-"))
                    .append(" | ").append(r.getOrDefault("latency_efficiency", "-"))
                    .append(" | ").append(r.getOrDefault("n", "-"))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    /** 表格里的竖线与换行必须转义，否则一份报告会被一个 Skill 名字切成两列。 */
    private static String md(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private static String nzText(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    public ProDtos.MatrixDetail detail(String code) {
        ProEntities.Matrix m = require(code);
        List<ProDtos.CellItem> cells = proMapper.cellsOf(code).stream().map(c -> {
            Skill s = skillMapper.findCardBySkillId(c.skillId);
            return new ProDtos.CellItem(c.id, c.skillId, s == null ? c.skillId : s.name,
                    c.agentCode, c.mcpCode, c.sceneCode, c.caseCode, c.inputText, c.expectedText,
                    c.outputText, c.reasoningText, c.status,
                    nz(c.latencyMs), nz(c.ttftMs),
                    c.scoreTotal == null ? 0 : c.scoreTotal.doubleValue(), c.error);
        }).toList();
        return new ProDtos.MatrixDetail(toItem(m), cells, parseJsonMap(m.summaryJson));
    }

    /** 分组对比：按 Skill / 按 Agent / 按参数三张表 + 排名 + 中文结论。 */
    public ProDtos.Compare compare(String code) {
        require(code);
        List<ProEntities.Cell> cells = proMapper.cellsOf(code).stream()
                .filter(c -> "DONE".equals(c.status)).toList();
        if (cells.isEmpty()) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "这个矩阵还没有完成的单元。怎么办：先点「执行」，跑完再看对比。");
        }

        Map<String, List<ProEntities.Cell>> bySkill = new LinkedHashMap<>();
        Map<String, List<ProEntities.Cell>> byAgent = new LinkedHashMap<>();
        Map<String, List<ProEntities.Cell>> byParams = new LinkedHashMap<>();
        for (ProEntities.Cell c : cells) {
            bySkill.computeIfAbsent(c.skillId, k -> new ArrayList<>()).add(c);
            byAgent.computeIfAbsent(c.agentCode, k -> new ArrayList<>()).add(c);
            byParams.computeIfAbsent(c.paramsJson == null ? "{}" : c.paramsJson, k -> new ArrayList<>())
                    .add(c);
        }
        List<Map<String, Object>> skillRows = aggregate(bySkill, "skill");
        List<Map<String, Object>> agentRows = aggregate(byAgent, "agent");
        List<Map<String, Object>> paramRows = aggregate(byParams, "params");
        rank(skillRows);
        rank(agentRows);
        rank(paramRows);

        String conclusion = String.format(Locale.ROOT,
                "共比较 %d 个单元（%d 个 Skill × %d 个 Agent × %d 组参数）。"
                        + "排名用 TOPSIS（准则：均分 权重 3、成功率 权重 2、时延效率 权重 1）。"
                        + "结论：最好的 Skill 是 %s，最好的 Agent 是 %s。",
                cells.size(), bySkill.size(), byAgent.size(), byParams.size(),
                topName(skillRows), topName(agentRows));
        return new ProDtos.Compare(code, skillRows, agentRows, paramRows, conclusion);
    }

    // ================================================================ 内部

    private List<Map<String, Object>> aggregate(Map<String, List<ProEntities.Cell>> groups, String keyName) {
        List<Map<String, Object>> out = new ArrayList<>();
        groups.forEach((key, list) -> {
            double avg = list.stream().mapToDouble(c -> dbl(c.scoreTotal)).average().orElse(0);
            double success = list.stream().filter(c -> "DONE".equals(c.status)).count() * 1.0 / list.size();
            double latencyEff = 1.0 - Math.min(1.0,
                    list.stream().mapToInt(c -> nz(c.latencyMs)).average().orElse(0) / 30000.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("skill".equals(keyName) ? "skill_id" : "agent".equals(keyName) ? "agent_code" : "params",
                    key);
            if ("skill".equals(keyName)) {
                Skill s = skillMapper.findCardBySkillId(key);
                row.put("name", s == null ? key : s.name);
            }
            row.put("n", list.size());
            row.put("avg_score", round(avg));
            row.put("success_rate", round(success));
            row.put("latency_efficiency", round(latencyEff));
            out.add(row);
        });
        return out;
    }

    /** TOPSIS 排名（准则：均分、成功率、时延效率）。 */
    private static void rank(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        double[][] matrix = new double[rows.size()][3];
        for (int i = 0; i < rows.size(); i++) {
            matrix[i][0] = (double) rows.get(i).get("avg_score");
            matrix[i][1] = (double) rows.get(i).get("success_rate");
            matrix[i][2] = (double) rows.get(i).get("latency_efficiency");
        }
        double[] scores = Metrics.topsis(matrix, new double[] {3, 2, 1},
                new boolean[] {true, true, true});
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("topsis", round(scores[i]));
            order.add(i);
        }
        order.sort((a, b) -> Double.compare((double) rows.get(b).get("topsis"),
                (double) rows.get(a).get("topsis")));
        for (int i = 0; i < order.size(); i++) {
            rows.get(order.get(i)).put("rank", i + 1);
        }
    }

    private static String topName(List<Map<String, Object>> rows) {
        return rows.stream().filter(r -> Integer.valueOf(1).equals(r.get("rank")))
                .map(r -> String.valueOf(r.getOrDefault("name", r.get("key"))))
                .findFirst().orElse("（无）");
    }

    private Map<String, Object> summarize(List<ProEntities.Cell> cells) {
        Map<String, Object> s = new LinkedHashMap<>();
        List<Double> scores = new ArrayList<>();
        for (ProEntities.Cell c : cells) {
            if ("DONE".equals(c.status)) {
                scores.add(dbl(c.scoreTotal));
            }
        }
        Metrics.Summary summary = Metrics.summary(scores);
        s.put("done", scores.size());
        s.put("total", cells.size());
        s.put("avg_score", round(summary.mean()));
        s.put("min_score", round(summary.min()));
        s.put("max_score", round(summary.max()));
        s.put("std", round(summary.std()));
        s.put("histogram", Metrics.histogram(scores, 5));
        return s;
    }

    private String buildPrompt(Skill skill, String scene) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个被 Skill 赋能的工作助手。请严格按下面这份手册做事，并在回答前先说明你的步骤。\n\n");
        sb.append("【Skill 名称】").append(skill.name).append('\n');
        sb.append("【Skill 说明】").append(Texts.firstNonBlank(skill.description, "（无）")).append('\n');
        sb.append("【行业场景】").append(CardFactory.sceneLabel(
                Texts.firstNonBlank(scene, skill.sceneCode))).append('\n');
        sb.append("【Skill 手册】\n").append(Texts.truncate(
                skill.content == null ? "" : skill.content, 5000)).append('\n');
        sb.append("【输出要求】先用 3-5 步说明做法，再给最终答复。\n");
        return sb.toString();
    }

    /** 参数网格：只接受 temperature / top_p / max_tokens 三个键。 */
    private static Map<String, List<Double>> normalizeGrid(Map<String, List<Double>> raw) {
        Map<String, List<Double>> out = new LinkedHashMap<>();
        if (raw == null) {
            out.put("temperature", List.of(0.3));
            return out;
        }
        for (Map.Entry<String, List<Double>> e : raw.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
            if (!List.of("temperature", "top_p", "max_tokens").contains(k)) {
                continue;
            }
            List<Double> values = e.getValue() == null ? List.of() : e.getValue();
            if (!values.isEmpty()) {
                out.put(k, values);
            }
        }
        if (out.isEmpty()) {
            out.put("temperature", List.of(0.3));
        }
        return out;
    }

    private static Map<String, List<Double>> singleValue(Map<String, List<Double>> grid) {
        Map<String, List<Double>> out = new LinkedHashMap<>();
        grid.forEach((k, v) -> out.put(k, List.of(v.get(0))));
        return out;
    }

    /** 笛卡尔展开。 */
    private static List<Map<String, Double>> expandGrid(Map<String, List<Double>> grid) {
        List<Map<String, Double>> out = new ArrayList<>();
        out.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<Double>> e : grid.entrySet()) {
            List<Map<String, Double>> next = new ArrayList<>();
            for (Map<String, Double> base : out) {
                for (Double v : e.getValue()) {
                    Map<String, Double> copy = new LinkedHashMap<>(base);
                    copy.put(e.getKey(), v);
                    next.add(copy);
                }
            }
            out = next;
        }
        return out;
    }

    /** 服务端自拟用例（用户没给 cases 时）：按场景给出两条通用问题。 */
    private static List<ProDtos.Case> defaultCases(String scene) {
        String s = Texts.firstNonBlank(scene, "general");
        List<ProDtos.Case> out = new ArrayList<>();
        out.add(new ProDtos.Case("auto-1", "用一句话说明这份手册能帮我做什么，并给出第一步该怎么做。",
                "说明用途并给出第一步"));
        out.add(new ProDtos.Case("auto-2", "按手册给我一个可执行的三步方案，每步一句话。",
                "三步方案，每步一句话"));
        if (!"general".equals(s)) {
            out.add(new ProDtos.Case("auto-3", "在「" + CardFactory.sceneLabel(s) + "」场景下，这份手册最需要注意什么？",
                    "给出场景相关的注意事项"));
        }
        return out;
    }

    private String pickDefaultSkill() {
        List<Skill> all = skillMapper.listAllCards();
        if (all.isEmpty()) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM,
                    "库里没有 Skill，无法创建实验。怎么办：先导入 Skill 库。");
        }
        return all.get(0).skillId;
    }

    /** 删除矩阵（连同单元）。 */
    @Transactional
    public void delete(String code) {
        require(code);
        proMapper.deleteCells(code);
        proMapper.deleteMatrix(code);
    }
    private ProEntities.Matrix require(String code) {
        ProEntities.Matrix m = proMapper.findMatrix(code);
        if (m == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "实验矩阵「" + code + "」");
        }
        return m;
    }

    private static ProDtos.MatrixItem toItem(ProEntities.Matrix m) {
        return new ProDtos.MatrixItem(m.code, m.name, m.matrixType, m.sceneCode, m.status,
                nz(m.plannedCells), nz(m.doneCells), m.skillIds, m.agentCodes, m.mcpCodes,
                Dates.format(m.createdAt), Dates.format(m.finishedAt));
    }

    private static Map<String, Double> parseParams(String json) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asDouble()));
        } catch (Exception ignore) {
            // 参数写坏就按默认值跑
        }
        return out;
    }

    private static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static double dbl(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
