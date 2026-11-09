package org.skillhub.adapter.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.port.McpPort;
import org.springframework.stereotype.Component;

/**
 * 内置 MCP（{@code builtin-calc}）：进程内实现，断网可用。
 *
 * <p>它暴露一个真实的工具语义 —— {@code skill_lookup}：在本地 Skill 库里做一次关键词检索，
 * 返回若干条命中（标题 + 分类 + 风险等级）。这样"用 MCP 增强上下文"这件事是真的发生了，
 * 只是数据源在本地。
 */
@Component
public class BuiltinMcpAdapter implements McpPort {

    public static final String CODE = "builtin-calc";

    private final LocalSkillLookup lookup;

    public BuiltinMcpAdapter(LocalSkillLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public Result call(Call call) {
        long start = System.currentTimeMillis();
        String query = String.valueOf(call.arguments().getOrDefault("query", ""));
        if (query.isBlank()) {
            query = String.valueOf(call.arguments().getOrDefault("input", ""));
        }
        List<String> hits = lookup.search(query, 3);
        int ms = (int) (System.currentTimeMillis() - start) + 5;
        if (hits.isEmpty()) {
            return new Result(true, "（内置工具 skill_lookup 没有找到与「" + trim(query) + "」相关的 Skill）",
                    ms, true, "");
        }
        StringBuilder sb = new StringBuilder("（以下来自内置工具 skill_lookup，本地 Skill 库命中 " + hits.size() + " 条）\n");
        for (String h : hits) {
            sb.append("- ").append(h).append('\n');
        }
        return new Result(true, sb.toString(), ms, true, "");
    }

    @Override
    public Map<String, Object> probe(String mcpCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("latency_ms", 1);
        out.put("detail", "内置工具，进程内实现，永远可用");
        out.put("tools", tools(mcpCode));
        return out;
    }

    @Override
    public List<String> tools(String mcpCode) {
        return List.of("skill_lookup");
    }

    private static String trim(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= 40 ? t : t.substring(0, 40) + "…";
    }

    /** 本地 Skill 检索（避免 adapter 直接依赖 business，用一个窄接口）。 */
    public interface LocalSkillLookup {
        List<String> search(String keyword, int limit);
    }
}
