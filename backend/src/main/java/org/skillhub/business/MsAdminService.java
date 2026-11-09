package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.skillhub.common.Actor;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.dto.MsDtos;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.mapper.MsMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.model.Skill;
import org.skillhub.support.Dates;
import org.skillhub.support.MsQueryParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill-MS 的"管理面"：把库当成数据库来查看与配置。
 *
 * <p>四类对象（逻辑库 / 索引 / 约束 / 事件）+ 两类台账（事件日志 / 事务台账），
 * 加上 {@code /ms/schema} 这个"元数据总览"——页面上的「库管理」全靠它。
 */
@Service
public class MsAdminService {

    private final MsMapper msMapper;
    private final SkillMapper skillMapper;
    private final EvalMapper evalMapper;
    private final MsEngine engine;

    public MsAdminService(MsMapper msMapper, SkillMapper skillMapper, EvalMapper evalMapper,
                          MsEngine engine) {
        this.msMapper = msMapper;
        this.skillMapper = skillMapper;
        this.evalMapper = evalMapper;
        this.engine = engine;
    }

    // ================================================================ 元数据

    /** {@code GET /api/v1/ms/schema}：把"库像数据库一样"讲清楚（含术语解释）。 */
    public MsDtos.Schema schema() {
        List<MsDtos.TableInfo> tables = List.of(
                table("skill", "Skill 表", "所有 Skill 的一行一条；content 是 SKILL.md 正文",
                        skillMapper.countAll(), List.of(
                                col("skill_id", "VARCHAR(191)", "稳定身份，如 lib:pdf"),
                                col("name", "VARCHAR(191)", "名字（唯一键候选）"),
                                col("category_code", "VARCHAR(48)", "分类（15 类之一）"),
                                col("scene_code", "VARCHAR(24)", "行业场景"),
                                col("risk_score", "INT", "0-100 风险分"),
                                col("risk_level", "VARCHAR(16)", "safe/low/medium/high/unrated"),
                                col("status", "VARCHAR(16)", "published/archived"),
                                col("skillset_code", "VARCHAR(64)", "所属逻辑库"),
                                col("content", "LONGTEXT", "SKILL.md 正文（列表查询不取）"))),
                table("skill_file", "附件表", "一个 Skill 目录下的每个文件", countOf("skill_file"),
                        List.of(col("skill_id", "VARCHAR(191)", "关联 Skill"),
                                col("path", "VARCHAR(512)", "相对路径"),
                                col("sha256", "VARCHAR(64)", "内容摘要"))),
                table("skillset", "逻辑库", "Skill 的\"分类抽屉\"，类比 database", msMapper.listSkillsets().size(),
                        List.of(col("code", "VARCHAR(64)", "库代号"),
                                col("skill_count", "INT", "派生计数"))),
                table("ms_constraint_def", "约束", "一条\"不许违反的规矩\"", msMapper.listConstraints().size(),
                        List.of(col("constraint_type", "VARCHAR(24)", "7 类之一"),
                                col("target_column", "VARCHAR(96)", "作用于哪一列"),
                                col("severity", "VARCHAR(16)", "ENFORCED/WARN"))),
                table("ms_event_def", "事件（触发器）", "\"发生某事就自动做某事\"", msMapper.listEvents().size(),
                        List.of(col("trigger_event", "VARCHAR(32)", "INSERT/UPDATE/DELETE/STATUS_CHANGE"),
                                col("action_type", "VARCHAR(24)", "TAG/AUDIT/STATUS/NOTIFY/EVALUATE"))),
                table("eval_run", "实测记录", "一次\"把 Skill 挂到智能体上跑\"的结果",
                        evalMapper.countRuns(null, null), List.of(
                                col("score_total", "DECIMAL(6,2)", "综合分 0-100"),
                                col("latency_ms", "INT", "总时延"),
                                col("ttft_ms", "INT", "首字时延"))));

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("skill", skillMapper.countAll());
        stats.put("skillset", (long) msMapper.listSkillsets().size());
        stats.put("index", (long) msMapper.listIndexes().size());
        stats.put("constraint", (long) msMapper.listConstraints().size());
        stats.put("event", (long) msMapper.listEvents().size());
        stats.put("tx_log", msMapper.countTxLogs(null));
        stats.put("event_log", msMapper.countEventLogs(null));

        return new MsDtos.Schema(tables, skillsets(), indexes(), constraints(), events(), stats, concepts());
    }

    /** 页面上"术语小词典"由这里驱动，保证三系统文案一致。 */
    public List<MsDtos.Concept> concepts() {
        return List.of(
                new MsDtos.Concept("逻辑库（Skillset）", "Skill 的\"分类抽屉\"，可以把不同团队/项目的 Skill 分开放。",
                        "像数据库里的一个 database"),
                new MsDtos.Concept("索引", "给 Skill 的某个属性建一个\"目录页\"，查起来更快，也让查询计划可解释。",
                        "像书的目录，或数据库的 index"),
                new MsDtos.Concept("约束", "一条\"不许违反的规矩\"，例如\"风险分不能超过 80\"。违反时会被拦下并告诉你改法。",
                        "像数据库的 CHECK / UNIQUE"),
                new MsDtos.Concept("事件（触发器）", "一件事发生后自动做另一件事，例如\"新增 Skill 就自动打标签\"。",
                        "像数据库的 trigger"),
                new MsDtos.Concept("事务台账", "每次写操作被哪条约束放行、动了哪些索引、触发了哪些事件，都记一笔。",
                        "像数据库的 redo/审计日志"),
                new MsDtos.Concept("查询台", "用一句类似 SQL 的话去查 Skill 库；只读，且只认白名单里的表和列。",
                        "像 DBMS 的 SQL 窗口"));
    }

    // ================================================================ 逻辑库

    public List<MsDtos.SetItem> skillsets() {
        return msMapper.listSkillsets().stream()
                .map(s -> new MsDtos.SetItem(s.code, s.name, s.description, s.owner,
                        (int) skillMapper.countBySkillset(s.code), s.status, Dates.format(s.createdAt)))
                .toList();
    }

    @Transactional
    public MsDtos.SetItem createSkillset(String code, String name, String description, Actor actor) {
        if (code == null || code.isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "code");
        }
        String c = code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (msMapper.findSkillset(c) != null) {
            throw ApiException.of(ErrorCode.CONFLICT, "逻辑库「" + c + "」已存在");
        }
        MsEntities.Skillset set = new MsEntities.Skillset();
        set.code = c;
        set.name = name == null || name.isBlank() ? c : name.trim();
        set.description = description == null ? "" : description.trim();
        set.owner = actor == null ? "guest" : actor.name();
        msMapper.insertSkillset(set);
        return skillsets().stream().filter(x -> x.code().equals(c)).findFirst().orElseThrow();
    }

    /** 删除逻辑库：非空则拒绝，并把"被哪条规则拦住"说清楚。 */
    @Transactional
    public void deleteSkillset(String code, Actor actor) {
        if ("default".equals(code)) {
            throw ApiException.ofMessage(ErrorCode.MS_CONSTRAINT_VIOLATION,
                    "被内置规则 [内置:default] 拦截：default 是内置逻辑库，不能删除。怎么办：把里面的 Skill 移到别的库，或改用别的库名。");
        }
        if (msMapper.findSkillset(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "逻辑库「" + code + "」");
        }
        long count = skillMapper.countBySkillset(code);
        if (count > 0) {
            throw ApiException.of(ErrorCode.MS_CONSTRAINT_VIOLATION, "REFERENCE:skillset",
                    "库里还有 " + count + " 个 Skill 引用它",
                    "先把这些 Skill 移到别的库（或用查询台批量改 skillset_code）");
        }
        msMapper.deleteSkillset(code);
    }

    // ================================================================ 索引

    public List<MsDtos.IndexItem> indexes() {
        return msMapper.listIndexes().stream()
                .map(d -> new MsDtos.IndexItem(d.code, d.name, d.skillsetCode, d.columnsCol, d.indexType,
                        Boolean.TRUE.equals(d.uniqueFlag), d.status,
                        d.entryCount == null ? 0 : d.entryCount, d.hitCount == null ? 0 : d.hitCount,
                        Dates.format(d.lastBuiltAt)))
                .toList();
    }

    @Transactional
    public MsDtos.IndexItem createIndex(String code, String name, String columns, String skillsetCode,
                                        boolean unique, Actor actor) {
        if (code == null || code.isBlank() || columns == null || columns.isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "code / columns_col");
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        MsEntities.IndexDef old = msMapper.findIndex(c);
        if (old != null) {
            msMapper.clearIndexEntries(c);
            msMapper.deleteIndex(c);
        }
        MsEntities.IndexDef def = new MsEntities.IndexDef();
        def.code = c;
        def.name = name == null || name.isBlank() ? c : name.trim();
        def.skillsetCode = skillsetCode == null || skillsetCode.isBlank() ? "default" : skillsetCode;
        def.columnsCol = normalizeColumns(columns);
        def.indexType = "BTREE";
        def.uniqueFlag = unique;
        msMapper.insertIndex(def);
        int entries = engine.rebuildIndex(c);
        return new MsDtos.IndexItem(c, def.name, def.skillsetCode, def.columnsCol, def.indexType,
                unique, "READY", entries, 0, Dates.format(java.time.LocalDateTime.now()));
    }

    @Transactional
    public Map<String, Object> rebuildIndex(String code) {
        int entries = engine.rebuildIndex(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("status", "READY");
        out.put("entry_count", entries);
        return out;
    }

    @Transactional
    public void dropIndex(String code) {
        if (msMapper.findIndex(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "索引「" + code + "」");
        }
        msMapper.clearIndexEntries(code);
        msMapper.updateIndexStatus(code, "DROPPED");
    }

    public List<MsDtos.IndexEntryItem> indexEntries(String code, int limit) {
        if (msMapper.findIndex(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "索引「" + code + "」");
        }
        return msMapper.listIndexEntries(code, Math.max(1, Math.min(limit, 200))).stream()
                .map(e -> new MsDtos.IndexEntryItem(e.skillId, e.keyValue))
                .toList();
    }

    // ================================================================ 约束

    public List<MsDtos.ConstraintItem> constraints() {
        return msMapper.listConstraints().stream()
                .map(c -> new MsDtos.ConstraintItem(c.code, c.name, c.skillsetCode, c.constraintType,
                        constraintLabel(c.constraintType), c.targetColumn, c.expression, c.severity,
                        c.status, c.violationCount == null ? 0 : c.violationCount,
                        Dates.format(c.lastViolatedAt)))
                .toList();
    }

    @Transactional
    public MsDtos.ConstraintItem saveConstraint(String code, String name, String type, String column,
                                                String expression, String severity, Actor actor) {
        if (code == null || code.isBlank() || type == null || type.isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "code / constraint_type");
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        MsEntities.ConstraintDef old = msMapper.findConstraint(c);
        MsEntities.ConstraintDef def = new MsEntities.ConstraintDef();
        def.code = c;
        def.name = name == null || name.isBlank() ? c : name.trim();
        def.skillsetCode = "default";
        def.constraintType = type.trim().toUpperCase(Locale.ROOT);
        def.targetColumn = column == null ? "" : column.trim();
        def.expression = expression == null ? "" : expression.trim();
        def.severity = "WARN".equalsIgnoreCase(severity) ? "WARN" : "ENFORCED";
        def.status = "ACTIVE";
        if (old == null) {
            msMapper.insertConstraint(def);
        } else {
            msMapper.updateConstraint(def);
        }
        return constraints().stream().filter(x -> x.code().equals(c)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteConstraint(String code) {
        if (msMapper.findConstraint(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "约束「" + code + "」");
        }
        msMapper.deleteConstraint(code);
    }

    /** 对全库跑一次校验，返回每条约束的违反数与样本。 */
    public List<MsDtos.ValidateItem> validateAll() {
        List<MsDtos.ValidateItem> out = new ArrayList<>();
        List<Skill> all = skillMapper.listAllCards();
        for (MsEntities.ConstraintDef def : msMapper.listConstraints()) {
            List<String> samples = new ArrayList<>();
            long violated = 0;
            for (Skill s : all) {
                var hits = engine.dryRun(s);
                for (MsEngine.PreviewHit h : hits) {
                    if (h.code().equals(def.code)) {
                        violated++;
                        if (samples.size() < 5) {
                            samples.add(s.skillId + " → " + h.message());
                        }
                    }
                }
            }
            out.add(new MsDtos.ValidateItem(def.code, def.name, def.constraintType, def.severity,
                    violated, samples));
        }
        return out;
    }

    // ================================================================ 事件

    public List<MsDtos.EventItem> events() {
        return msMapper.listEvents().stream()
                .map(e -> new MsDtos.EventItem(e.code, e.name, e.skillsetCode, e.triggerEvent,
                        e.conditionExpr, e.actionType, actionLabel(e.actionType), e.actionPayload,
                        Boolean.TRUE.equals(e.enabled), e.firedCount == null ? 0 : e.firedCount))
                .toList();
    }

    @Transactional
    public MsDtos.EventItem saveEvent(String code, String name, String trigger, String condition,
                                      String actionType, String payload, Boolean enabled, Actor actor) {
        if (code == null || code.isBlank() || trigger == null || actionType == null) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "code / trigger_event / action_type");
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        MsEntities.EventDef old = msMapper.findEvent(c);
        MsEntities.EventDef def = new MsEntities.EventDef();
        def.code = c;
        def.name = name == null || name.isBlank() ? c : name.trim();
        def.skillsetCode = "default";
        def.triggerEvent = trigger.trim().toUpperCase(Locale.ROOT);
        def.conditionExpr = condition == null ? "" : condition.trim();
        def.actionType = actionType.trim().toUpperCase(Locale.ROOT);
        def.actionPayload = payload == null ? "" : payload.trim();
        def.enabled = enabled == null || enabled;
        if (old == null) {
            msMapper.insertEvent(def);
        } else {
            msMapper.updateEvent(def);
        }
        return events().stream().filter(x -> x.code().equals(c)).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteEvent(String code) {
        if (msMapper.findEvent(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "事件「" + code + "」");
        }
        msMapper.deleteEvent(code);
    }

    public PageResult<MsDtos.EventLogItem> eventLogs(PageQuery query, String status) {
        String st = status == null || status.isBlank() ? null : status;
        long total = msMapper.countEventLogs(st);
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<MsDtos.EventLogItem> items = msMapper
                .listEventLogs(st, query.getLimit(), query.getOffset()).stream()
                .map(l -> new MsDtos.EventLogItem(l.id, l.eventCode, l.triggerOn, l.skillId, l.actionType,
                        l.payload, l.status, l.attempts == null ? 0 : l.attempts, l.lastError,
                        Dates.format(l.createdAt)))
                .toList();
        return PageResult.of(query, total, items);
    }

    /** 重试一条死信：把状态打回 PENDING（下次派发会再试）。 */
    @Transactional
    public MsDtos.EventLogItem retryEventLog(long id) {
        MsEntities.EventLog row = msMapper.findEventLog(id);
        if (row == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "事件日志 #" + id);
        }
        if (!"DEAD".equals(row.status) && !"FAILED".equals(row.status)) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "这条日志状态是 " + row.status + "，不需要重试。怎么办：只有 FAILED / DEAD 才需要重试。");
        }
        row.status = "PENDING";
        row.lastError = "";
        msMapper.updateEventLog(row);
        return eventLogs(new PageQuery(), "PENDING").items().stream()
                .filter(x -> x.id() == id).findFirst()
                .orElse(new MsDtos.EventLogItem(id, row.eventCode, row.triggerOn, row.skillId,
                        row.actionType, row.payload, row.status, row.attempts, "", ""));
    }

    // ================================================================ 台账

    public PageResult<MsDtos.TxItem> txLogs(PageQuery query, String skillId) {
        String sid = skillId == null || skillId.isBlank() ? null : skillId;
        long total = msMapper.countTxLogs(sid);
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<MsDtos.TxItem> items = msMapper.listTxLogs(sid, query.getLimit(), query.getOffset()).stream()
                .map(t -> new MsDtos.TxItem(t.id, t.txId, t.opType, t.skillId, t.actor, t.verdict,
                        t.constraintHits, t.indexTouched, t.eventsFired, Dates.format(t.createdAt)))
                .toList();
        return PageResult.of(query, total, items);
    }

    // ================================================================ 工具

    public List<Map<String, Object>> queryableTables() {
        Map<String, List<String>> desc = MsQueryParser.describe();
        List<Map<String, Object>> out = new ArrayList<>();
        desc.forEach((t, cols) -> out.add(Map.of("table", t, "columns", cols)));
        return out;
    }

    private static String normalizeColumns(String columns) {
        return java.util.Arrays.stream(columns.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private static String constraintLabel(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "NOT_NULL" -> "非空";
            case "UNIQUE" -> "唯一";
            case "RANGE" -> "数值区间";
            case "ENUM" -> "枚举白名单";
            case "REGEX" -> "正则格式";
            case "CHECK_EXPR" -> "表达式";
            case "REFERENCE" -> "引用完整性";
            default -> type;
        };
    }

    private static String actionLabel(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "TAG" -> "打标签";
            case "STATUS" -> "改状态";
            case "AUDIT" -> "写审计";
            case "NOTIFY" -> "发通知";
            case "EVALUATE" -> "转评估";
            default -> type;
        };
    }

    private static MsDtos.TableInfo table(String name, String label, String desc, long rows,
                                         List<MsDtos.Column> cols) {
        return new MsDtos.TableInfo(name, label, desc, rows, cols);
    }

    private static MsDtos.Column col(String name, String type, String meaning) {
        return new MsDtos.Column(name, type, meaning);
    }

    private long countOf(String table) {
        try {
            return skillMapper.countAll();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
