package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.MsDtos;
import org.skillhub.mapper.MsMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.support.Dates;
import org.springframework.stereotype.Service;

/**
 * 对象树与 DDL：把 Skill-MS 的对象当成数据库对象来"浏览"和"导出"。
 *
 * <p>为什么单独一个服务，而不是塞进 {@link MsAdminService}：
 * <ul>
 *   <li>{@code MsAdminService} 是<b>写</b>的面（建索引、改约束、删库），每个方法都要过引擎、写台账；</li>
 *   <li>这里是<b>读</b>的面（树、DDL 文本），不写任何一行数据，也不开事务；</li>
 *   <li>读面会被页面频繁调用（打开一次对象页就要一次树），与写面分开后，事务边界一目了然。</li>
 * </ul>
 *
 * <p><b>DDL 到底是什么</b>：本站的索引/约束/事件是<b>元数据 + 实体表</b>（{@code ms_index_def} +
 * {@code ms_index_entry}），不是真实的 B 树或触发器。所以这里给出的 DDL 是
 * <b>声明式描述</b>：借 SQL 的形状让人一眼看懂"这个对象在管什么"，
 * 同时给出三样能验证的东西 —— {@code equivalent_sql}（能在本站查询台真跑）、
 * {@code api}（能真的改系统）、{@code rebuild_api}（能真的重建）。
 * 如果只给一段漂亮的 CREATE INDEX 而不给这三样，就等于给了一个不可验证的承诺。
 */
@Service
public class MsObjectService {

    /** 对象类型白名单：写死在这里，避免把任意字符串拼进错误信息或页面标签。 */
    private static final List<String> TYPES = List.of("skillset", "index", "constraint", "event");

    private final MsMapper msMapper;

    public MsObjectService(MsMapper msMapper) {
        this.msMapper = msMapper;
    }

    // ================================================================ 对象树

    /**
     * {@code GET /api/v1/ms/objects}：一棵"库 → 索引 / 约束 / 事件"的树。
     *
     * <p>排序规则刻意与页面一致：库按 code 升序；索引/约束/事件按 code 升序。
     * 不用 created_at 排序是因为对象很少（个位数到十几个），按名字排比按时间排更容易"找得到"。
     */
    public MsDtos.ObjectTree tree() {
        List<MsEntities.IndexDef> indexes = msMapper.listIndexes();
        List<MsEntities.ConstraintDef> constraints = msMapper.listConstraints();
        List<MsEntities.EventDef> events = msMapper.listEvents();

        List<MsDtos.SkillsetNode> nodes = new ArrayList<>();
        for (MsEntities.Skillset set : msMapper.listSkillsets()) {
            String code = set.code;
            List<MsDtos.ObjectNode> setIndexes = new ArrayList<>();
            for (MsEntities.IndexDef d : indexes) {
                if (!belongs(code, d.skillsetCode)) {
                    continue;
                }
                long entries = msMapper.countIndexEntries(d.code);
                setIndexes.add(new MsDtos.ObjectNode("index", d.code, d.name,
                        (blank(d.columnsCol) ? "未指定列" : d.columnsCol)
                                + (Boolean.TRUE.equals(d.uniqueFlag) ? " · 唯一" : "")
                                + " · " + entries + " 条目录",
                        entries));
            }
            List<MsDtos.ObjectNode> setConstraints = new ArrayList<>();
            for (MsEntities.ConstraintDef d : constraints) {
                if (!belongs(code, d.skillsetCode)) {
                    continue;
                }
                setConstraints.add(new MsDtos.ObjectNode("constraint", d.code, d.name,
                        constraintLabel(d.constraintType) + " · " + d.severity
                                + (blank(d.targetColumn) ? "" : " · " + d.targetColumn), 0));
            }
            List<MsDtos.ObjectNode> setEvents = new ArrayList<>();
            for (MsEntities.EventDef d : events) {
                if (!belongs(code, d.skillsetCode)) {
                    continue;
                }
                setEvents.add(new MsDtos.ObjectNode("event", d.code, d.name,
                        d.triggerEvent + " → " + actionLabel(d.actionType)
                                + (Boolean.FALSE.equals(d.enabled) ? " · 已停用" : ""), 0));
            }
            nodes.add(new MsDtos.SkillsetNode(code, set.name, set.description,
                    msMapper.countSkillsInSet(code), setIndexes, setConstraints, setEvents,
                    msMapper.countTxLogs(null)));
        }

        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("skillset", (long) nodes.size());
        totals.put("index", (long) indexes.size());
        totals.put("constraint", (long) constraints.size());
        totals.put("event", (long) events.size());
        totals.put("tx_log", msMapper.countTxLogs(null));

        return new MsDtos.ObjectTree(nodes, totals, Dates.format(now()));
    }

    // ================================================================ DDL

    /**
     * {@code GET /api/v1/ms/ddl?type=index&code=idx_cat}：一个对象的 DDL 卡。
     *
     * <p>找不到对象时抛 {@link ErrorCode#NOT_FOUND}（1101），而不是返回一段空 DDL ——
     * "没有这个对象"和"这个对象没有 DDL"是两件事，不能混。
     */
    public MsDtos.DdlDoc ddl(String type, String code) {
        String t = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM,
                    "对象类型只能是 " + String.join(" / ", TYPES) + "，收到的是「" + type + "」");
        }
        if (blank(code)) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM, "必须给出对象编号（code）");
        }
        return switch (t) {
            case "skillset" -> skillsetDdl(code);
            case "index" -> indexDdl(code);
            case "constraint" -> constraintDdl(code);
            default -> eventDdl(code);
        };
    }

    private MsDtos.DdlDoc skillsetDdl(String code) {
        MsEntities.Skillset set = msMapper.findSkillset(code);
        if (set == null) {
            throw notFound("逻辑库", code);
        }
        long skillCount = msMapper.countSkillsInSet(code);
        String ddl = "-- 逻辑库（相当于一个 database）：把不同成熟度的 Skill 分开放\n"
                + "CREATE SKILLSET " + q(set.code) + "\n"
                + "  NAME " + q(set.name) + "\n"
                + "  OWNER " + q(blank(set.owner) ? "library" : set.owner) + "\n"
                + "  ENABLED TRUE;\n"
                + "\n"
                + "-- 当前成员：" + skillCount + " 个 Skill\n"
                + "-- 默认库 default 是内置的，删除会被引擎拒绝";
        return new MsDtos.DdlDoc("skillset", set.code, "逻辑库 " + set.code, ddl,
                List.of("逻辑库不复制数据：移动 Skill 只是改 skill.skillset_code 这一列。",
                        "库下所有索引 / 约束 / 事件都只对「本库」的写操作生效。",
                        "成员数含已归档 Skill（列表页默认只显示 published）。"),
                "SELECT skillset_code, COUNT(*) AS n FROM skill GROUP BY skillset_code",
                "POST /api/v1/ms/skillsets  {\"code\":\"" + set.code + "\",\"name\":\"...\"}",
                "DELETE /api/v1/ms/skillsets/" + set.code + "（default 不可删）");
    }

    private MsDtos.DdlDoc indexDdl(String code) {
        MsEntities.IndexDef def = msMapper.findIndex(code);
        if (def == null) {
            throw notFound("索引", code);
        }
        long entries = msMapper.countIndexEntries(def.code);
        String columns = blank(def.columnsCol) ? "<未指定>" : def.columnsCol;
        String ddl = "-- 索引：给某几列建「目录页」，查询可以先查目录再回表\n"
                + "CREATE " + (Boolean.TRUE.equals(def.uniqueFlag) ? "UNIQUE " : "") + "INDEX "
                + q(def.code) + "\n"
                + "  ON SKILLSET " + q(def.skillsetCode) + "\n"
                + (blank(def.columnsCol) ? "" : "  COLUMNS (" + def.columnsCol + ")\n")
                + "  TYPE " + (blank(def.indexType) ? "HASH" : def.indexType) + ";\n"
                + "\n"
                + "-- 已建目录项：" + entries + " 条 · 状态 " + (blank(def.status) ? "unknown" : def.status);
        String firstCol = firstColumn(def.columnsCol);
        return new MsDtos.DdlDoc("index", def.code, "索引 " + def.code, ddl,
                List.of("本站的索引是「元数据 + ms_index_entry 实体表」，不是数据库内核的 B 树。",
                        "写入 Skill 时引擎会同步维护目录项；也可以手动重建（见下方 rebuild）。",
                        "唯一索引会让第二行同键写入被引擎拒绝。"),
                blank(firstCol) ? "SELECT COUNT(*) AS n FROM skill"
                        : "SELECT skill_id, name FROM skill ORDER BY " + firstCol + " LIMIT 10",
                "POST /api/v1/ms/indexes  {\"code\":\"" + def.code + "\",\"columns\":\"" + columns + "\"}",
                "POST /api/v1/ms/indexes/" + def.code + "/rebuild");
    }

    private MsDtos.DdlDoc constraintDdl(String code) {
        MsEntities.ConstraintDef def = msMapper.findConstraint(code);
        if (def == null) {
            throw notFound("约束", code);
        }
        String ddl = "-- 约束：写操作必须先过这一关，违反就拦下来\n"
                + "ALTER SKILLSET " + q(def.skillsetCode) + "\n"
                + "  ADD CONSTRAINT " + q(def.code) + "\n"
                + "  TYPE " + (blank(def.constraintType) ? "EXPRESSION" : def.constraintType) + "\n"
                + (blank(def.targetColumn) ? "" : "  ON (" + def.targetColumn + ")\n")
                + "  CHECK (" + (blank(def.expression) ? "TRUE" : def.expression) + ")\n"
                + "  SEVERITY " + (blank(def.severity) ? "ERROR" : def.severity) + ";";
        return new MsDtos.DdlDoc("constraint", def.code, "约束 " + def.code, ddl,
                List.of("SEVERITY=ERROR 时违反即拒绝；WARN 只记录不拦截（台账里能看到）。",
                        "表达式使用的字段必须是 Skill 表里的列名，否则校验阶段就会报错。",
                        "可以先点「全量校验」看有没有现存的违规行，再决定上不上 ERROR。"),
                "SELECT * FROM skill WHERE NOT (" + (blank(def.expression) ? "TRUE" : def.expression) + ") LIMIT 20",
                "POST /api/v1/ms/constraints  {\"code\":\"" + def.code + "\",\"expression\":\"...\"}",
                "POST /api/v1/ms/constraints/validate");
    }

    private MsDtos.DdlDoc eventDdl(String code) {
        MsEntities.EventDef def = msMapper.findEvent(code);
        if (def == null) {
            throw notFound("事件", code);
        }
        String ddl = "-- 事件：发生某事就自动做某事（触发条件 + 动作）\n"
                + "CREATE EVENT " + q(def.code) + "\n"
                + "  ON SKILLSET " + q(def.skillsetCode) + "\n"
                + "  AFTER " + (blank(def.triggerEvent) ? "UPDATE" : def.triggerEvent) + "\n"
                + "  WHEN (" + (blank(def.conditionExpr) ? "TRUE" : def.conditionExpr) + ")\n"
                + "  DO " + (blank(def.actionType) ? "NOTIFY" : def.actionType)
                + (blank(def.actionPayload) ? "" : " " + def.actionPayload) + "\n"
                + "  ENABLED " + (Boolean.FALSE.equals(def.enabled) ? "FALSE" : "TRUE") + ";";
        return new MsDtos.DdlDoc("event", def.code, "事件 " + def.code, ddl,
                List.of("事件是同步派发的：写操作返回前就已经执行，失败会记进事件日志。",
                        "action_payload 里可以用 ${skill_id} 占位，派发时替换成真实值。",
                        "停用事件不会删除历史日志；重试按钮只重跑失败的那一条。"),
                "SELECT event_code, status, COUNT(*) AS n FROM ms_event_log GROUP BY event_code, status",
                "POST /api/v1/ms/events  {\"code\":\"" + def.code + "\",\"trigger\":\""
                        + (blank(def.triggerEvent) ? "UPDATE" : def.triggerEvent) + "\"}",
                "POST /api/v1/ms/events/logs/{id}/retry");
    }

    // ================================================================ 内部

    private static boolean belongs(String skillsetCode, String objectSet) {
        String s = blank(objectSet) ? "default" : objectSet;
        return s.equals(blank(skillsetCode) ? "default" : skillsetCode);
    }

    /** 把 code 渲染成 SQL 里能直接粘贴的样子；空值给 <未命名> 而不是 null。 */
    private static String q(String code) {
        return code == null ? "<未命名>" : code;
    }

    private static String firstColumn(String columns) {
        if (blank(columns)) {
            return "";
        }
        String first = columns.split(",")[0].trim();
        return first.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static String constraintLabel(String type) {
        if (type == null) {
            return "表达式";
        }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "NOT_NULL" -> "非空";
            case "UNIQUE" -> "唯一";
            case "RANGE" -> "取值范围";
            case "REFERENCE" -> "引用存在";
            case "EXPRESSION" -> "自定义表达式";
            default -> type;
        };
    }

    private static String actionLabel(String type) {
        if (type == null) {
            return "通知";
        }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "NOTIFY" -> "发通知";
            case "REINDEX" -> "重建索引";
            case "TAG" -> "打标签";
            case "WEBHOOK" -> "调外部接口";
            default -> type;
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static ApiException notFound(String what, String code) {
        return ApiException.ofMessage(ErrorCode.NOT_FOUND,
                "找不到" + what + "「" + code + "」。怎么办：在「对象」页刷新一下，"
                        + "或先用 GET /api/v1/ms/objects 看现在有哪些对象。");
    }

    private static java.time.LocalDateTime now() {
        return java.time.LocalDateTime.now();
    }
}
