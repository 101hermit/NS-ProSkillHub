package org.skillhub.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.skillhub.model.MsEntities;

/**
 * 约束求值器（Skill-MS 的"规矩"在这里落地）。
 *
 * <p>7 类约束，全部在一个纯函数里判定，因此可以脱离数据库单测：
 * <table border="1">
 *   <tr><th>类型</th><th>expression 写法</th><th>含义</th></tr>
 *   <tr><td>NOT_NULL</td><td>（空）</td><td>目标列不能为空</td></tr>
 *   <tr><td>UNIQUE</td><td>（空）</td><td>目标列在全库唯一（可排除自身）</td></tr>
 *   <tr><td>RANGE</td><td>{@code min=0,max=80}</td><td>数值区间（可只写一边）</td></tr>
 *   <tr><td>ENUM</td><td>{@code published,archived}</td><td>枚举白名单</td></tr>
 *   <tr><td>REGEX</td><td>{@code ^[a-z0-9-]+$}</td><td>正则匹配</td></tr>
 *   <tr><td>CHECK_EXPR</td><td>{@code risk_score<=80}</td><td>单列二元比较</td></tr>
 *   <tr><td>REFERENCE</td><td>{@code skillset_code->skillset.code}</td><td>引用完整性</td></tr>
 * </table>
 *
 * <p>{@code severity = ENFORCED} 时违反即拒绝（422）；{@code WARN} 时放行但记告警。
 */
public final class ConstraintEvaluator {

    /** 判定结果。{@code passed=false} 且 {@code warnOnly=false} 时应拒绝写操作。 */
    public record Verdict(boolean passed, String message, String fix) {
        public static Verdict pass() {
            return new Verdict(true, "", "");
        }

        public static Verdict fail(String message, String fix) {
            return new Verdict(false, message, fix);
        }
    }

    /** 需要查库的判定（唯一性 / 引用完整性）由调用方注入，保持本类纯函数。 */
    public interface Judge {
        /** 排除 {@code excludeSkillId} 后，值为 {@code value} 的行数。 */
        long countOther(String column, String value, String excludeSkillId);

        /** {@code table.column} 里是否存在值 {@code value}。 */
        boolean referenceExists(String table, String column, String value);
    }

    private static final Map<String, Pattern> REGEX_CACHE = new LinkedHashMap<>();

    private ConstraintEvaluator() {
    }

    /**
     * @param def     约束定义
     * @param row     候选行（key 为 snake_case 列名）
     * @param exclude 排除的 skill_id（更新时排除自身，可为 null）
     * @param judge   查库回调（唯一性/引用用；不需要时传 null）
     */
    public static Verdict evaluate(MsEntities.ConstraintDef def, Map<String, Object> row,
                                   String exclude, Judge judge) {
        String type = def.constraintType == null ? "" : def.constraintType.toUpperCase(Locale.ROOT);
        String col = def.targetColumn == null ? "" : def.targetColumn.trim();
        Object raw = row.get(col);
        String value = raw == null ? "" : String.valueOf(raw);

        return switch (type) {
            case "NOT_NULL" -> value.isBlank()
                    ? Verdict.fail("列 " + col + " 不能为空", "给这个 Skill 填上 " + col)
                    : Verdict.pass();
            case "UNIQUE" -> {
                if (judge == null || value.isBlank()) {
                    yield Verdict.pass();
                }
                long n = judge.countOther(col, value, exclude);
                yield n > 0
                        ? Verdict.fail("列 " + col + " 的值「" + value + "」已被另外 " + n + " 个 Skill 占用",
                        "改一个不重复的 " + col)
                        : Verdict.pass();
            }
            case "RANGE" -> checkRange(col, value, def.expression);
            case "ENUM" -> {
                List<String> allowed = splitCsv(def.expression);
                yield allowed.isEmpty() || allowed.contains(value)
                        ? Verdict.pass()
                        : Verdict.fail("列 " + col + " 只能是 " + String.join(" / ", allowed) + "，实际是「" + value + "」",
                        "改成允许值之一");
            }
            case "REGEX" -> {
                String re = def.expression == null ? "" : def.expression.trim();
                if (re.isEmpty()) {
                    yield Verdict.pass();
                }
                boolean ok;
                try {
                    Pattern p = REGEX_CACHE.computeIfAbsent(re, Pattern::compile);
                    ok = p.matcher(value).find();
                } catch (RuntimeException e) {
                    yield Verdict.pass(); // 写坏的正则不应让业务瘫痪，交给"约束校验"页提示
                }
                yield ok ? Verdict.pass()
                        : Verdict.fail("列 " + col + " 的值「" + value + "」不满足格式 " + re,
                        "按 " + re + " 的格式填写");
            }
            case "CHECK_EXPR" -> checkExpression(col, value, def.expression, row);
            case "REFERENCE" -> {
                String expr = def.expression == null ? "" : def.expression.trim();
                int arrow = expr.indexOf("->");
                if (judge == null || arrow < 0) {
                    yield Verdict.pass();
                }
                String target = expr.substring(arrow + 2).trim();
                int dot = target.indexOf('.');
                if (dot < 0) {
                    yield Verdict.pass();
                }
                String table = target.substring(0, dot);
                String targetCol = target.substring(dot + 1);
                boolean exists = judge.referenceExists(table, targetCol, value);
                yield exists
                        ? Verdict.pass()
                        : Verdict.fail("列 " + col + " 的值「" + value + "」在 " + table + "." + targetCol + " 里找不到",
                        "先在 " + table + " 里建这个值，或改用已存在的值");
            }
            default -> Verdict.pass();
        };
    }

    private static Verdict checkRange(String col, String value, String expr) {
        Double min = null;
        Double max = null;
        for (String part : splitCsv(expr)) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = part.substring(eq + 1).trim();
            try {
                if ("min".equals(k)) {
                    min = Double.parseDouble(v);
                } else if ("max".equals(k)) {
                    max = Double.parseDouble(v);
                }
            } catch (NumberFormatException ignore) {
                // 表达式写坏：视为不约束
                return Verdict.pass();
            }
        }
        double num;
        try {
            num = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Verdict.fail("列 " + col + " 需要是数字，实际是「" + value + "」", "填一个数字");
        }
        if (min != null && num < min) {
            return Verdict.fail("列 " + col + " 是 " + (long) num + "，小于下限 " + (long) (double) min,
                    "让它不小于 " + (long) (double) min);
        }
        if (max != null && num > max) {
            return Verdict.fail("列 " + col + " 是 " + (long) num + "，超过上限 " + (long) (double) max,
                    "让它不超过 " + (long) (double) max);
        }
        return Verdict.pass();
    }

    /** 支持 {@code col op value} 形式，{@code op} ∈ {@code = != > >= < <=}。 */
    private static Verdict checkExpression(String col, String value, String expr, Map<String, Object> row) {
        if (expr == null || expr.isBlank()) {
            return Verdict.pass();
        }
        String e = expr.replace(" ", "");
        String[] ops = {"<=", ">=", "!=", "<", ">", "="};
        for (String op : ops) {
            int idx = e.indexOf(op);
            if (idx <= 0) {
                continue;
            }
            String left = e.substring(0, idx);
            String right = e.substring(idx + op.length());
            Object leftVal = row.getOrDefault(left, value);
            return compare(left, String.valueOf(leftVal), op, right);
        }
        return Verdict.pass();
    }

    private static Verdict compare(String col, String left, String op, String right) {
        Double ln = parseOrNull(left);
        Double rn = parseOrNull(right);
        boolean ok;
        if (ln != null && rn != null) {
            ok = switch (op) {
                case "<" -> ln < rn;
                case "<=" -> ln <= rn;
                case ">" -> ln > rn;
                case ">=" -> ln >= rn;
                case "!=" -> !ln.equals(rn);
                default -> ln.equals(rn);
            };
        } else {
            int c = left.compareTo(right);
            ok = switch (op) {
                case "<" -> c < 0;
                case "<=" -> c <= 0;
                case ">" -> c > 0;
                case ">=" -> c >= 0;
                case "!=" -> c != 0;
                default -> c == 0;
            };
        }
        return ok ? Verdict.pass()
                : Verdict.fail("列 " + col + " 的值「" + left + "」不满足 " + left + op + right,
                "让 " + col + " 满足 " + exprHint(op, right));
    }

    private static String exprHint(String op, String right) {
        return switch (op) {
            case "<" -> "小于 " + right;
            case "<=" -> "不超过 " + right;
            case ">" -> "大于 " + right;
            case ">=" -> "不小于 " + right;
            case "!=" -> "不等于 " + right;
            default -> "等于 " + right;
        };
    }

    private static Double parseOrNull(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toList();
    }
}
