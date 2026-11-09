package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.MsDtos;
import org.skillhub.mapper.MsMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.support.MsQueryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 查询台：把用户写的类 SQL 语句**解析 → 校验 → 用固定模板执行**。
 *
 * <p>安全边界（三条都必须成立）：
 * <ol>
 *   <li>表名、列名全部走 {@link MsQueryParser} 的白名单，**用户输入永不进 SQL 的标识符位置**</li>
 *   <li>值一律用 {@code ?} 参数绑定，不拼字符串</li>
 *   <li>只读：语句必须以 {@code SELECT} 开头，表名白名单里没有任何可写入口</li>
 * </ol>
 *
 * <p>"查询计划"是真的：它去查 {@code ms_index_def}，看这个 WHERE 用的列上有没有 READY 的索引，
 * 并顺手把命中数 +1（这样索引命中统计才有意义）。
 */
@Service
public class MsQueryService {

    private static final Logger log = LoggerFactory.getLogger(MsQueryService.class);

    /** 逻辑表名 → 物理表名（页面上用人类友好的名字，执行时换成真实表）。 */
    private static final java.util.Map<String, String> PHYSICAL = java.util.Map.of(
            "skills", "skill",
            "skillsets", "skillset",
            "indexes", "ms_index_def",
            "constraints", "ms_constraint_def",
            "events", "ms_event_def",
            "tx_log", "ms_tx_log");

    private final JdbcTemplate jdbc;
    private final MsMapper msMapper;

    public MsQueryService(JdbcTemplate jdbc, MsMapper msMapper) {
        this.jdbc = jdbc;
        this.msMapper = msMapper;
    }

    /** 执行查询。 */
    public MsDtos.QueryResult run(String sql) {
        MsQueryParser.Query q = parse(sql);
        StringBuilder sb = new StringBuilder("SELECT ");
        if (q.countStar()) {
            sb.append("COUNT(*) AS ").append(q.alias() == null ? "n" : safeIdent(q.alias()));
        } else {
            sb.append(String.join(", ", q.columns()));
        }
        sb.append(" FROM ").append(physical(q.table()));

        List<Object> args = new ArrayList<>();
        appendWhere(sb, q, args);
        if (q.orderBy() != null) {
            sb.append(" ORDER BY ").append(q.orderBy()).append(' ').append(q.order());
        }
        sb.append(" LIMIT ").append(q.limit()).append(" OFFSET ").append(q.offset());

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(sb.toString(), args.toArray());
        long elapsed = System.currentTimeMillis() - start;

        List<String> columns = new ArrayList<>();
        if (!rows.isEmpty()) {
            columns.addAll(rows.get(0).keySet());
        } else if (!q.countStar()) {
            columns.addAll(q.columns());
        } else {
            columns.add(q.alias() == null ? "n" : q.alias());
        }
        if (q.countStar() && rows.size() == 1) {
            return new MsDtos.QueryResult(columns,
                    List.of(new LinkedHashMap<>(rows.get(0))), 1, elapsed, sql);
        }
        return new MsDtos.QueryResult(columns, rows, rows.size(), elapsed, sql);
    }

    /** 只看计划，不执行。 */
    public MsDtos.QueryPlan plan(String sql) {
        MsQueryParser.Query q = parse(sql);
        String whereCol = q.where().isEmpty() ? null : q.where().get(0).column();
        String usedIndex = null;
        String usedColumns = "";
        if (whereCol != null) {
            for (MsEntities.IndexDef def : msMapper.listIndexes()) {
                if (!"READY".equalsIgnoreCase(def.status)) {
                    continue;
                }
                for (String col : def.columnsCol.split(",")) {
                    if (col.trim().equalsIgnoreCase(whereCol)) {
                        usedIndex = def.code;
                        usedColumns = def.columnsCol;
                        msMapper.touchIndexHit(def.code);
                        break;
                    }
                }
                if (usedIndex != null) {
                    break;
                }
            }
        }
        int estimated = (int) Math.min(1000, countOf(q.table()));
        String explanation = usedIndex != null
                ? "WHERE 命中了索引「" + usedIndex + "」（列 " + usedColumns + "），走索引查找，预估扫描量远小于全表 "
                        + estimated + " 行。"
                : (whereCol == null
                        ? "没有 WHERE 条件，必然全表扫描（" + estimated + " 行）。"
                        : "WHERE 用的列「" + whereCol + "」上没有 READY 索引，走全表扫描（" + estimated
                                + " 行）。怎么办：到「索引」页给这一列建一个索引再重建。");
        return new MsDtos.QueryPlan(q.table(), usedIndex == null ? "FULL_SCAN" : "INDEX_SCAN",
                usedIndex, whereCol, estimated, usedIndex != null, explanation);
    }

    // ------------------------------------------------------------------ 内部

    private MsQueryParser.Query parse(String sql) {
        try {
            return MsQueryParser.parse(sql);
        } catch (MsQueryParser.ParseException e) {
            String cols = availableColumns(e);
            throw ApiException.of(ErrorCode.QUERY_REJECTED, e.position, e.token, cols);
        }
    }

    private static String availableColumns(MsQueryParser.ParseException e) {
        StringBuilder sb = new StringBuilder();
        MsQueryParser.TABLES.forEach((t, cols) -> sb.append(t).append("(")
                .append(String.join("/", cols.stream().sorted().toList())).append(") "));
        return sb.toString().trim();
    }

    private void appendWhere(StringBuilder sb, MsQueryParser.Query q, List<Object> args) {
        if (q.where().isEmpty()) {
            return;
        }
        sb.append(" WHERE ");
        List<String> parts = new ArrayList<>();
        for (MsQueryParser.Cond c : q.where()) {
            String ident = safeIdent(c.column());
            switch (c.op()) {
                case "IN" -> {
                    parts.add(ident + " IN (" + String.join(",", c.values().stream().map(v -> "?").toList()) + ")");
                    args.addAll(c.values());
                }
                case "BETWEEN" -> {
                    parts.add(ident + " BETWEEN ? AND ?");
                    args.add(c.values().get(0));
                    args.add(c.values().get(1));
                }
                case "LIKE" -> {
                    parts.add(ident + " LIKE ?");
                    String v = c.values().get(0);
                    args.add(v.contains("%") ? v : "%" + v + "%");
                }
                default -> {
                    parts.add(ident + " " + c.op() + " ?");
                    args.add(c.values().get(0));
                }
            }
        }
        sb.append(String.join(" AND ", parts));
    }

    /** 标识符只允许字母数字下划线（解析器已经白名单校验过，这里是第二道闸）。 */
    private static String safeIdent(String ident) {
        if (ident == null || !ident.matches("[A-Za-z0-9_]+")) {
            throw ApiException.ofMessage(ErrorCode.QUERY_REJECTED, "标识符「" + ident + "」不是一个合法的列名。");
        }
        return ident.toLowerCase(Locale.ROOT);
    }

    /** 逻辑表名换成物理表名（白名单已在解析器里校验过）。 */
    private static String physical(String logicalTable) {
        String t = PHYSICAL.get(logicalTable);
        return t == null ? safeIdent(logicalTable) : t;
    }

    private long countOf(String table) {
        try {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + physical(table), Long.class);
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.warn("统计行数失败：{}", e.getMessage());
            return 0;
        }
    }
}
