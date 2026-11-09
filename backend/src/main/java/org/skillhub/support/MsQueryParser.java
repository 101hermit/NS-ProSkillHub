package org.skillhub.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Skill-MS 查询台的**受限 SQL 解析器**。
 *
 * <p>设计原则：<b>绝不把用户输入拼进 SQL</b>。
 * 这里把语句解析成结构化的 {@link Query}（表名、列名、条件、排序、分页），
 * 全部经过白名单校验，再由 {@link org.skillhub.business.MsQueryService} 用固定模板 + 参数绑定执行。
 *
 * <p>支持的语法（其余一律拒绝，并指出第几个词不认识）：
 * <pre>
 * SELECT &lt;cols|*&gt; | COUNT(*) [AS n]
 * FROM   &lt;table&gt;
 * [WHERE &lt;cond&gt; [AND &lt;cond&gt;]…]
 * [ORDER BY &lt;col&gt; [ASC|DESC]]
 * [LIMIT &lt;n&gt; [OFFSET &lt;m&gt;]]
 *
 * cond := &lt;col&gt; (= | != | &lt;&gt; | &gt; | &gt;= | &lt; | &lt;= | LIKE | IN | BETWEEN) &lt;value&gt;
 * value := '字符串' | 数字 | true | false | (v1, v2, …) | v1 AND v2
 * </pre>
 */
public final class MsQueryParser {

    /** 允许查询的表 → 允许的列。 */
    public static final Map<String, Set<String>> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("skills", Set.of(
                "skill_id", "slug", "name", "title", "description", "category_code", "scene_code", "tags",
                "version", "source", "source_url", "license", "author", "author_kind", "risk_score",
                "risk_level", "status", "download_count", "install_count", "file_count", "package_bytes",
                "content_bytes", "eval_count", "avg_score", "skillset_code", "created_at", "updated_at"));
        TABLES.put("skillsets", Set.of("code", "name", "owner", "skill_count", "status", "created_at"));
        TABLES.put("indexes", Set.of("code", "name", "skillset_code", "columns_col", "index_type",
                "unique_flag", "status", "entry_count", "hit_count"));
        TABLES.put("constraints", Set.of("code", "name", "constraint_type", "target_column", "expression",
                "severity", "status", "violation_count"));
        TABLES.put("events", Set.of("code", "name", "trigger_event", "action_type", "enabled", "fired_count"));
        TABLES.put("tx_log", Set.of("tx_id", "op_type", "skill_id", "actor", "verdict", "created_at"));
    }

    /** 表别名 → 真实表名（页面用的是人类友好的名字）。 */
    private static final Map<String, String> ALIASES = Map.of(
            "skill", "skills",
            "skillset", "skillsets",
            "index", "indexes",
            "constraint", "constraints",
            "event", "events");

    /** 解析失败时抛这个，{@code position} 用于"第几个词不认识"。 */
    public static class ParseException extends RuntimeException {
        public final int position;
        public final String token;

        public ParseException(int position, String token, String message) {
            super(message);
            this.position = position;
            this.token = token;
        }
    }

    /** 一个条件。{@code op} 已大写归一化。 */
    public record Cond(String column, String op, List<String> values) {
    }

    /** 解析结果。 */
    public record Query(String table, List<String> columns, boolean countStar, String alias,
                        List<Cond> where, String orderBy, String order, int limit, int offset) {
    }

    private MsQueryParser() {
    }

    /** 解析一条语句。 */
    public static Query parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ParseException(1, "", "语句是空的。怎么办：写一条 SELECT … FROM … 语句。");
        }
        List<String> tokens = tokenize(sql);
        Cursor c = new Cursor(tokens);

        String first = c.next("语句必须以 SELECT 开头");
        if (!"SELECT".equalsIgnoreCase(first)) {
            throw new ParseException(c.pos, first, "语句必须以 SELECT 开头（查询台只读，不支持 INSERT/UPDATE/DELETE）");
        }

        boolean countStar = false;
        List<String> columns = new ArrayList<>();
        String alias = null;

        if (c.peekIs("COUNT")) {
            c.pop();
            expect(c, "(");
            expect(c, "*");
            expect(c, ")");
            countStar = true;
            if (c.peekIs("AS")) {
                c.pop();
                alias = c.next("AS 后面要跟一个别名");
            }
        } else if (c.peekIs("*")) {
            c.pop();
        } else {
            while (true) {
                String col = c.next("SELECT 后面要跟列名");
                columns.add(requireColumn(null, col, c.pos));
                if (c.peekIs(",")) {
                    c.pop();
                    continue;
                }
                break;
            }
        }

        expectWord(c, "FROM", "SELECT 之后必须是 FROM");
        String rawTable = c.next("FROM 后面要跟表名").toLowerCase(Locale.ROOT);
        String table = ALIASES.getOrDefault(rawTable, rawTable);
        if (!TABLES.containsKey(table)) {
            throw new ParseException(c.pos, rawTable,
                    "没有这张表「" + rawTable + "」。怎么办：可用表 " + String.join(" / ", TABLES.keySet()));
        }
        for (String col : columns) {
            requireColumn(table, col, c.pos);
        }

        List<Cond> where = new ArrayList<>();
        if (c.peekIs("WHERE")) {
            c.pop();
            while (true) {
                String col = requireColumn(table, c.next("WHERE 后面要跟列名"), c.pos);
                String op = c.next("列名后面要跟比较符").toUpperCase(Locale.ROOT);
                if ("<>".equals(op)) {
                    op = "!=";
                }
                if (!Set.of("=", "!=", ">", ">=", "<", "<=", "LIKE", "IN", "BETWEEN").contains(op)) {
                    throw new ParseException(c.pos, op,
                            "不支持的比较符「" + op + "」。怎么办：可用 = != > >= < <= LIKE IN BETWEEN");
                }
                List<String> values = new ArrayList<>();
                if ("IN".equals(op)) {
                    expect(c, "(");
                    while (true) {
                        values.add(c.next("IN 里要有值"));
                        if (c.peekIs(",")) {
                            c.pop();
                            continue;
                        }
                        break;
                    }
                    expect(c, ")");
                } else if ("BETWEEN".equals(op)) {
                    values.add(c.next("BETWEEN 需要两个值"));
                    expectWord(c, "AND", "BETWEEN 的写法是 BETWEEN a AND b");
                    values.add(c.next("BETWEEN 需要第二个值"));
                } else {
                    values.add(c.next("比较符后面要跟一个值"));
                }
                where.add(new Cond(col, op, values));
                if (c.peekIs("AND")) {
                    c.pop();
                    continue;
                }
                break;
            }
        }

        String orderBy = null;
        String order = "ASC";
        if (c.peekIs("ORDER")) {
            c.pop();
            expectWord(c, "BY", "ORDER 后面必须是 BY");
            orderBy = requireColumn(table, c.next("ORDER BY 后面要跟列名"), c.pos);
            if (c.peekIs("ASC") || c.peekIs("DESC")) {
                order = c.pop().toUpperCase(Locale.ROOT);
            }
        }

        int limit = 50;
        int offset = 0;
        if (c.peekIs("LIMIT")) {
            c.pop();
            limit = parseInt(c.next("LIMIT 后面要跟数字"), c.pos);
            if (c.peekIs("OFFSET")) {
                c.pop();
                offset = parseInt(c.next("OFFSET 后面要跟数字"), c.pos);
            }
        }
        if (c.hasMore()) {
            String extra = c.peek();
            throw new ParseException(c.pos, extra, "第 " + c.pos + " 个词「" + extra + "」之后的内容看不懂");
        }
        return new Query(table, columns, countStar, alias, where, orderBy, order,
                Math.max(1, Math.min(limit, 500)), Math.max(0, offset));
    }

    /** 供前端展示的元数据（表 → 可用列）。 */
    public static Map<String, List<String>> describe() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        TABLES.forEach((t, cols) -> out.put(t, cols.stream().sorted().toList()));
        return out;
    }

    // ------------------------------------------------------------------ 内部

    private static String requireColumn(String table, String col, int pos) {
        String c = col.toLowerCase(Locale.ROOT);
        if (table != null && !TABLES.get(table).contains(c)) {
            throw new ParseException(pos, col,
                    "表 " + table + " 没有列「" + col + "」。怎么办：可用列："
                            + String.join(", ", TABLES.get(table).stream().sorted().toList()));
        }
        return c;
    }

    private static int parseInt(String s, int pos) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new ParseException(pos, s, "这里要一个数字，实际是「" + s + "」");
        }
    }

    private static void expectWord(Cursor c, String word, String message) {
        String t = c.next(message);
        if (!word.equalsIgnoreCase(t)) {
            throw new ParseException(c.pos, t, message + "（实际看到「" + t + "」）");
        }
    }

    private static void expect(Cursor c, String sym) {
        String t = c.next("这里应该是 " + sym);
        if (!sym.equals(t)) {
            throw new ParseException(c.pos, t, "这里应该是 " + sym + "（实际看到「" + t + "」）");
        }
    }

    /** 把语句切成 token，保留单引号里的字符串与括号、比较符。 */
    static List<String> tokenize(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inQuote) {
                if (ch == '\'') {
                    inQuote = false;
                    out.add(buf.toString());
                    buf.setLength(0);
                } else {
                    buf.append(ch);
                }
                continue;
            }
            if (ch == '\'') {
                flush(buf, out);
                inQuote = true;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                flush(buf, out);
                continue;
            }
            if (ch == '(' || ch == ')' || ch == ',') {
                flush(buf, out);
                out.add(String.valueOf(ch));
                continue;
            }
            if (ch == '!' && i + 1 < sql.length() && sql.charAt(i + 1) == '=') {
                flush(buf, out);
                out.add("!=");
                i++;
                continue;
            }
            if (ch == '>' || ch == '<') {
                boolean hasEq = i + 1 < sql.length() && sql.charAt(i + 1) == '=';
                flush(buf, out);
                out.add(ch + (hasEq ? "=" : ""));
                if (hasEq) {
                    i++;
                }
                continue;
            }
            if (ch == '=') {
                flush(buf, out);
                out.add("=");
                continue;
            }
            buf.append(ch);
        }
        flush(buf, out);
        if (inQuote) {
            out.add(buf.toString());
        }
        return out;
    }

    private static void flush(StringBuilder buf, List<String> out) {
        if (buf.length() > 0) {
            out.add(buf.toString());
            buf.setLength(0);
        }
    }

    /** 简单的指针游标。 */
    private static final class Cursor {
        private final List<String> tokens;
        private int idx = 0;
        private int pos = 0;

        Cursor(List<String> tokens) {
            this.tokens = tokens;
        }

        boolean hasMore() {
            return idx < tokens.size();
        }

        String peek() {
            return idx < tokens.size() ? tokens.get(idx) : "";
        }

        boolean peekIs(String word) {
            return idx < tokens.size() && word.equalsIgnoreCase(tokens.get(idx));
        }

        String pop() {
            String t = tokens.get(idx++);
            pos++;
            return t;
        }

        String next(String message) {
            if (idx >= tokens.size()) {
                throw new ParseException(tokens.size() + 1, "", "语句在这里结束了：" + message);
            }
            return pop();
        }
    }
}
