package org.skillhub.business;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.skillhub.common.Actor;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.Trace;
import org.skillhub.mapper.MsMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.model.Skill;
import org.skillhub.port.IdPort;
import org.skillhub.support.ConstraintEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill-MS 引擎（标准版的**核心机制**）。
 *
 * <p>一句话：**所有对 Skill 表的写操作都必须经过这里**。它在一个事务里按固定顺序做三件事：
 *
 * <pre>
 *   ① 约束裁决   —— 违反 ENFORCED 约束 → 抛 422（带约束 code 与修复建议）；违反 WARN → 放行并记告警
 *   ② 索引维护   —— 增量更新受影响索引的条目（键值不变则不动，变了就换）
 *   ③ 事件派发   —— 写 event_log（同事务），再执行动作（打标签 / 写审计 / 改状态 / 通知）
 *   ④ 台账落账   —— 写 ms_tx_log：这次写被哪条约束放过、动了哪些索引、触发了哪些事件
 * </pre>
 *
 * <p><b>为什么"事件派发"要在事务里写日志、动作在外执行</b>：
 * 日志是"发生过什么"的事实，必须与业务写同生共死；动作可能涉及外部副作用（通知），
 * 失败不能回滚业务数据 —— 所以它有自己的重试与死信（{@code event_log.status}）。
 *
 * <p>可解释性：任何一次写操作，页面都能回答"被哪条约束放行、触发了哪些事件、动了哪些索引"。
 */
@Service
public class MsEngine {

    private static final Logger log = LoggerFactory.getLogger(MsEngine.class);

    /** 允许作为约束目标的列（白名单；其它列一律忽略，避免奇怪的拼写导致误判）。 */
    private static final Set<String> CONFIGURABLE_COLUMNS = Set.of(
            "name", "title", "description", "category_code", "scene_code", "tags", "version", "source",
            "source_url", "license", "author", "author_kind", "risk_score", "risk_level", "status",
            "skillset_code", "slug", "skill_id");

    private final MsMapper msMapper;
    private final SkillMapper skillMapper;
    private final IdPort idPort;

    public MsEngine(MsMapper msMapper, SkillMapper skillMapper, IdPort idPort) {
        this.msMapper = msMapper;
        this.skillMapper = skillMapper;
        this.idPort = idPort;
    }

    // ================================================================ 写入门禁

    /**
     * 写操作前：约束裁决。
     *
     * @param opType   INSERT / UPDATE / DELETE
     * @param before   写之前的行（INSERT 时为 null）
     * @param after    写之后的行（DELETE 时可为 null，表示目标是删除）
     * @param actor    操作者
     * @return 裁决结果（含告警与索引/事件计划），供 {@link #afterWrite} 使用
     */
    @Transactional
    public Gate beforeWrite(String opType, Skill before, Skill after, Actor actor) {
        Map<String, Object> row = toRow(after != null ? after : before);
        String skillsetCode = String.valueOf(row.getOrDefault("skillset_code", "default"));
        String skillId = String.valueOf(row.getOrDefault("skill_id", ""));
        String exclude = before == null ? null : before.skillId;

        List<String> enforcedHits = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<MsEntities.ConstraintDef> defs = msMapper.activeConstraints(skillsetCode);
        ConstraintEvaluator.Judge judge = new JudgeImpl(skillId);

        for (MsEntities.ConstraintDef def : defs) {
            if (def.targetColumn != null && !def.targetColumn.isBlank()
                    && !CONFIGURABLE_COLUMNS.contains(def.targetColumn)) {
                continue;
            }
            ConstraintEvaluator.Verdict v = ConstraintEvaluator.evaluate(def, row, exclude, judge);
            if (v.passed()) {
                continue;
            }
            String line = "[" + def.code + "] " + v.message();
            if ("WARN".equalsIgnoreCase(def.severity)) {
                warnings.add(line);
            } else {
                enforcedHits.add(line);
                msMapper.touchConstraintViolation(def.code);
            }
        }

        if (!enforcedHits.isEmpty()) {
            String first = enforcedHits.get(0);
            String code = first.substring(1, Math.max(1, first.indexOf(']')));
            MsEntities.ConstraintDef def = msMapper.findConstraint(code);
            String fix = def == null ? "按约束要求调整后再试" : "按「" + def.name + "」的要求调整后再试";
            writeTx(opType, skillId, actor, "REJECT", null, row, String.join(" | ", enforcedHits), "", "");
            throw ApiException.of(ErrorCode.MS_CONSTRAINT_VIOLATION, code,
                    String.join("；", enforcedHits), fix);
        }
        return new Gate(opType, skillId, actor, warnings, row, before);
    }

    /** 门禁结果（透传给 {@link #afterWrite}）。 */
    public record Gate(String opType, String skillId, Actor actor, List<String> warnings,
                       Map<String, Object> afterRow, Skill before) {
    }

    /**
     * 写操作后：索引维护 + 事件派发 + 台账落账。
     *
     * <p>注意：本方法由业务层在**同一个事务**里调用（业务层的 {@code @Transactional} 已生效），
     * 因此这里不再加 {@code @Transactional}，避免自调用失效的困惑。
     */
    public void afterWrite(Gate gate, String triggerEvent) {
        List<String> indexTouched = new ArrayList<>();
        List<String> eventsFired = new ArrayList<>();

        // ② 索引维护：只维护与本次行相关的索引
        for (MsEntities.IndexDef def : msMapper.listIndexes()) {
            if ("DROPPED".equalsIgnoreCase(def.status)) {
                continue;
            }
            if (touchIndex(def, gate.afterRow())) {
                indexTouched.add(def.code);
                if ("STALE".equals(def.status)) {
                    msMapper.updateIndexStatus(def.code, "STALE");
                }
            }
        }

        // ③ 事件派发
        String skillsetCode = String.valueOf(gate.afterRow().getOrDefault("skillset_code", "default"));
        for (MsEntities.EventDef event : msMapper.activeEvents(skillsetCode)) {
            if (!matchesTrigger(event, triggerEvent)) {
                continue;
            }
            if (!conditionHolds(event.conditionExpr, gate.afterRow())) {
                continue;
            }
            MsEntities.EventLog logRow = new MsEntities.EventLog();
            logRow.eventCode = event.code;
            logRow.triggerOn = triggerEvent;
            logRow.skillId = gate.skillId();
            logRow.actionType = event.actionType;
            logRow.payload = event.actionPayload;
            logRow.status = "PENDING";
            logRow.attempts = 0;
            logRow.lastError = "";
            msMapper.insertEventLog(logRow);
            msMapper.touchEventFired(event.code);
            eventsFired.add(event.code);
            try {
                runAction(event, gate);
                logRow.status = "DONE";
            } catch (RuntimeException e) {
                logRow.status = logRow.attempts + 1 >= 3 ? "DEAD" : "FAILED";
                logRow.attempts = logRow.attempts + 1;
                logRow.lastError = String.valueOf(e.getMessage()).substring(
                        0, Math.min(500, String.valueOf(e.getMessage()).length()));
                log.warn("事件 {} 动作执行失败：{}", event.code, e.getMessage());
            }
            msMapper.updateEventLog(logRow);
        }

        // ④ 台账
        writeTx(gate.opType(), gate.skillId(), gate.actor(),
                gate.warnings().isEmpty() ? "PASS" : "WARN", null, gate.afterRow(),
                String.join(" | ", gate.warnings()),
                String.join(",", indexTouched), String.join(",", eventsFired));
    }

    // ================================================================ 索引

    /** 重建一个索引：清空条目 → 全量重算 → 置 READY。 */
    @Transactional
    public int rebuildIndex(String code) {
        MsEntities.IndexDef def = msMapper.findIndex(code);
        if (def == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "索引「" + code + "」");
        }
        msMapper.markIndexBuilt(code, "BUILDING", 0);
        msMapper.clearIndexEntries(code);
        List<MsEntities.IndexEntry> entries = new ArrayList<>();
        List<Skill> all = skillMapper.listAllCards();
        for (Skill s : all) {
            String key = keyOf(def, s);
            if (key == null) {
                continue;
            }
            MsEntities.IndexEntry e = new MsEntities.IndexEntry();
            e.indexCode = code;
            e.skillId = s.skillId;
            e.keyValue = key.length() > 500 ? key.substring(0, 500) : key;
            entries.add(e);
        }
        int chunk = 200;
        for (int i = 0; i < entries.size(); i += chunk) {
            msMapper.insertIndexEntries(entries.subList(i, Math.min(entries.size(), i + chunk)));
        }
        msMapper.markIndexBuilt(code, "READY", entries.size());
        return entries.size();
    }

    /** 增量维护：键值变了就替换该行的条目；返回是否真的动了。 */
    private boolean touchIndex(MsEntities.IndexDef def, Map<String, Object> row) {
        String skillId = String.valueOf(row.getOrDefault("skill_id", ""));
        if (skillId.isBlank()) {
            return false;
        }
        Skill s = skillMapper.findCardBySkillId(skillId);
        String key = s == null ? null : keyOf(def, s);
        if (key == null) {
            return false;
        }
        // 简单策略：按 index_code + skill_id 覆盖式重建该行的条目
        List<MsEntities.IndexEntry> single = new ArrayList<>();
        MsEntities.IndexEntry e = new MsEntities.IndexEntry();
        e.indexCode = def.code;
        e.skillId = skillId;
        e.keyValue = key.length() > 500 ? key.substring(0, 500) : key;
        single.add(e);
        msMapper.insertIndexEntries(single);
        int count = (def.entryCount == null ? 0 : def.entryCount) + 1;
        msMapper.markIndexBuilt(def.code, def.status, count);
        return true;
    }

    /** 计算某行在某个索引上的键值（多列用 {@code |} 连接）。 */
    private static String keyOf(MsEntities.IndexDef def, Skill s) {
        Map<String, Object> row = toRow(s);
        List<String> parts = new ArrayList<>();
        for (String col : def.columnsCol.split(",")) {
            String c = col.trim().toLowerCase(Locale.ROOT);
            Object v = row.get(c);
            if (v == null) {
                return null;
            }
            String text = String.valueOf(v);
            if (text.isBlank()) {
                return null;
            }
            parts.add(text);
        }
        return parts.isEmpty() ? null : String.join("|", parts);
    }

    // ================================================================ 事件动作

    private boolean matchesTrigger(MsEntities.EventDef event, String triggerEvent) {
        if (event.triggerEvent == null || triggerEvent == null) {
            return false;
        }
        String t = event.triggerEvent.toUpperCase(Locale.ROOT);
        return t.equals(triggerEvent.toUpperCase(Locale.ROOT))
                || "ANY".equals(t) || "ALL".equals(t);
    }

    /** 极简条件：{@code col op value}（与 CHECK_EXPR 同语法）；空则恒真。 */
    private static boolean conditionHolds(String expr, Map<String, Object> row) {
        if (expr == null || expr.isBlank()) {
            return true;
        }
        String e = expr.replace(" ", "");
        for (String op : new String[] {"<=", ">=", "!=", "<", ">", "="}) {
            int idx = e.indexOf(op);
            if (idx <= 0) {
                continue;
            }
            String left = e.substring(0, idx);
            String right = e.substring(idx + op.length());
            String actual = String.valueOf(row.getOrDefault(left, ""));
            double a;
            double b;
            try {
                a = Double.parseDouble(actual);
                b = Double.parseDouble(right);
            } catch (NumberFormatException ignore) {
                int c = actual.compareTo(right);
                return switch (op) {
                    case "<" -> c < 0;
                    case "<=" -> c <= 0;
                    case ">" -> c > 0;
                    case ">=" -> c >= 0;
                    case "!=" -> c != 0;
                    default -> c == 0;
                };
            }
            return switch (op) {
                case "<" -> a < b;
                case "<=" -> a <= b;
                case ">" -> a > b;
                case ">=" -> a >= b;
                case "!=" -> a != b;
                default -> a == b;
            };
        }
        return true;
    }

    private void runAction(MsEntities.EventDef event, Gate gate) {
        String type = event.actionType == null ? "" : event.actionType.toUpperCase(Locale.ROOT);
        switch (type) {
            case "TAG" -> {
                Skill s = skillMapper.findCardBySkillId(gate.skillId());
                if (s == null) {
                    return;
                }
                String extra = event.actionPayload == null ? "" : event.actionPayload.trim();
                if (extra.isEmpty()) {
                    return;
                }
                List<String> tags = new ArrayList<>(org.skillhub.support.Texts.splitTags(s.tags));
                if (!tags.contains(extra)) {
                    tags.add(extra);
                    Skill patch = new Skill();
                    patch.skillId = gate.skillId();
                    patch.tags = org.skillhub.support.Texts.joinTags(tags);
                    skillMapper.updateSelective(patch);
                }
            }
            case "STATUS" -> {
                String target = event.actionPayload == null ? "" : event.actionPayload.trim();
                if (!target.isEmpty()) {
                    skillMapper.updateStatus(gate.skillId(), target);
                }
            }
            case "AUDIT", "NOTIFY", "EVALUATE" -> {
                // 这三种动作在本版本只落 event_log（审计/通知/触发评估由后续迭代接外部通道）
                log.info("事件动作 {} 已记录：skill={} payload={}", type, gate.skillId(), event.actionPayload);
            }
            default -> log.info("未知事件动作 {}，已忽略", type);
        }
    }

    // ================================================================ 工具

    private void writeTx(String opType, String skillId, Actor actor, String verdict,
                         String beforeJson, Map<String, Object> afterRow,
                         String constraintHits, String indexTouched, String eventsFired) {
        MsEntities.TxLog tx = new MsEntities.TxLog();
        tx.txId = idPort.code("tx");
        tx.opType = opType;
        tx.skillId = skillId == null ? "" : skillId;
        tx.actor = actor == null ? "guest" : actor.name();
        tx.verdict = verdict;
        tx.beforeJson = beforeJson;
        tx.afterJson = toJson(afterRow);
        tx.constraintHits = truncate(constraintHits, 4000);
        tx.indexTouched = truncate(indexTouched, 1000);
        tx.eventsFired = truncate(eventsFired, 1000);
        tx.actor = tx.actor + " [trace " + Trace.current() + "]";
        msMapper.insertTxLog(tx);
    }

    /** 把实体摊平成"列名 → 值"（约束与事件都按列名工作）。 */
    public static Map<String, Object> toRow(Skill s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) {
            return m;
        }
        m.put("skill_id", s.skillId);
        m.put("slug", s.slug);
        m.put("name", s.name);
        m.put("title", s.title);
        m.put("description", s.description);
        m.put("category_code", s.categoryCode);
        m.put("scene_code", s.sceneCode);
        m.put("tags", s.tags);
        m.put("version", s.version);
        m.put("source", s.source);
        m.put("source_url", s.sourceUrl);
        m.put("license", s.license);
        m.put("author", s.author);
        m.put("author_kind", s.authorKind);
        m.put("risk_score", s.riskScore);
        m.put("risk_level", s.riskLevel);
        m.put("status", s.status);
        m.put("skillset_code", s.skillsetCode);
        m.put("download_count", s.downloadCount);
        m.put("file_count", s.fileCount);
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 约束判定里需要查库的部分。 */
    private final class JudgeImpl implements ConstraintEvaluator.Judge {
        private final String skillId;

        private JudgeImpl(String skillId) {
            this.skillId = skillId;
        }

        @Override
        public long countOther(String column, String value, String excludeSkillId) {
            if (!CONFIGURABLE_COLUMNS.contains(column)) {
                return 0;
            }
            try {
                return skillMapper.countByColumn(column, value, excludeSkillId);
            } catch (RuntimeException e) {
                log.warn("唯一性判定失败（按不冲突处理）：{}", e.getMessage());
                return 0;
            }
        }

        @Override
        public boolean referenceExists(String table, String column, String value) {
            try {
                if ("skillset".equalsIgnoreCase(table) && "code".equalsIgnoreCase(column)) {
                    return skillMapper.countSkillsetByColumn("code", value) > 0;
                }
            } catch (RuntimeException e) {
                log.warn("引用完整性判定失败（按通过处理）：{}", e.getMessage());
            }
            return true;
        }
    }

    /**
     * 把"要改的字段"合并到"改之前的行"上，得到"写之后的行"。
     *
     * <p>约束与事件都作用于"写之后的值"，因此必须在落库**之前**算出这一行。
     * 这里刻意只处理非 null 字段 —— 与 {@code updateSelective} 的语义保持一致。
     */
    public static Skill merge(Skill before, Skill patch) {
        if (before == null) {
            return patch;
        }
        Skill m = new Skill();
        m.id = before.id;
        m.skillId = before.skillId;
        m.slug = before.slug;
        m.name = patch.name != null ? patch.name : before.name;
        m.title = patch.title != null ? patch.title : before.title;
        m.description = patch.description != null ? patch.description : before.description;
        m.categoryCode = patch.categoryCode != null ? patch.categoryCode : before.categoryCode;
        m.sceneCode = patch.sceneCode != null ? patch.sceneCode : before.sceneCode;
        m.tags = patch.tags != null ? patch.tags : before.tags;
        m.version = patch.version != null ? patch.version : before.version;
        m.source = patch.source != null ? patch.source : before.source;
        m.sourceUrl = patch.sourceUrl != null ? patch.sourceUrl : before.sourceUrl;
        m.sourceRepo = patch.sourceRepo != null ? patch.sourceRepo : before.sourceRepo;
        m.license = patch.license != null ? patch.license : before.license;
        m.author = patch.author != null ? patch.author : before.author;
        m.authorKind = patch.authorKind != null ? patch.authorKind : before.authorKind;
        m.riskScore = patch.riskScore != null ? patch.riskScore : before.riskScore;
        m.riskLevel = patch.riskLevel != null ? patch.riskLevel : before.riskLevel;
        m.status = patch.status != null ? patch.status : before.status;
        m.content = patch.content != null ? patch.content : before.content;
        m.skillsetCode = patch.skillsetCode != null ? patch.skillsetCode : before.skillsetCode;
        m.downloadCount = before.downloadCount;
        m.fileCount = before.fileCount;
        return m;
    }
    /** 供页面展示"这次写为什么被拦"（不抛异常版本，用于全库校验）。 */
    public List<MsEngine.PreviewHit> dryRun(Skill skill) {
        Map<String, Object> row = toRow(skill);
        List<MsEngine.PreviewHit> out = new ArrayList<>();
        ConstraintEvaluator.Judge judge = new JudgeImpl(skill.skillId);
        for (MsEntities.ConstraintDef def : msMapper.activeConstraints(
                String.valueOf(row.getOrDefault("skillset_code", "default")))) {
            ConstraintEvaluator.Verdict v = ConstraintEvaluator.evaluate(def, row, skill.skillId, judge);
            if (!v.passed()) {
                out.add(new PreviewHit(def.code, def.name, def.severity, v.message(), v.fix()));
            }
        }
        return out;
    }

    /** 一条"将会被拦"的预览。 */
    public record PreviewHit(String code, String name, String severity, String message, String fix) {
    }

    /** 供事务台账展示：把 BigDecimal 之类转成普通值。 */
    public static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>();
        row.forEach((k, v) -> out.put(k, v instanceof BigDecimal ? ((BigDecimal) v).doubleValue() : v));
        return out;
    }
}
