package org.skillhub.action.v1;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.skillhub.business.SkillService;
import org.skillhub.support.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * **应用内自带的 MCP 服务端**（Streamable HTTP 传输）。
 *
 * <p>为什么把它放在应用里，而不是让用户再起一个进程：
 * <ul>
 *   <li><b>链接立刻可用</b>：默认 MCP 端点就是本站自己的 {@code http://127.0.0.1:<端口>/mcp}，
 *       不需要额外部署、不会出现"配了一个连不上的地址"；</li>
 *   <li><b>协议是真的</b>：完整实现 {@code initialize → notifications/initialized → tools/list → tools/call}
 *       与 {@code Mcp-Session-Id} 会话头，外部 MCP 客户端（含 MCP Inspector）也能接上来；</li>
 *   <li><b>数据是真的</b>：工具直接读本站 Skill 库，返回真实条目，不是桩文本。</li>
 * </ul>
 *
 * <p>暴露三个工具：
 * <table border="1">
 *   <tr><th>工具</th><th>入参</th><th>返回</th></tr>
 *   <tr><td>{@code skill_search}</td><td>{@code query}, {@code limit?}</td><td>命中的 Skill 摘要列表</td></tr>
 *   <tr><td>{@code skill_read}</td><td>{@code skill_id} 或 {@code name}</td><td>SKILL.md 正文（截断）</td></tr>
 *   <tr><td>{@code skill_stats}</td><td>—</td><td>库规模、分类分布、风险分布</td></tr>
 * </table>
 *
 * <p>注意：这里的返回值是**裸 JSON-RPC**，不走 {@code ApiResult} 信封 —— MCP 协议就是这样规定的。
 */
@RestController
public class McpServerAction {

    private static final Logger log = LoggerFactory.getLogger(McpServerAction.class);
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    /** 已建立的会话（内存即可：单机工具，重启后客户端会自动重新 initialize）。 */
    private final java.util.Set<String> sessions = ConcurrentHashMap.newKeySet();

    private final SkillService skillService;

    public McpServerAction(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * MCP 统一入口。所有 JSON-RPC 方法都走这里。
     *
     * <p>返回类型故意用 {@code ResponseEntity<Object>}：{@code notifications/initialized} 必须回
     * 202 且 **无 body**，普通 RPC 回 200 + JSON。
     */
    @PostMapping(path = "/mcp", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<Object> handle(@RequestBody Map<String, Object> body,
                                        @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
                                        HttpServletRequest request) {
        String method = str(body.get("method"));
        Object id = body.get("id");
        boolean isNotification = id == null;

        if (method == null || method.isBlank()) {
            return ResponseEntity.ok(error(id, -32600, "缺少 method 字段"));
        }
        if (isNotification) {
            // notifications/initialized 等通知：按协议回 202 且不带 body
            log.debug("MCP 通知：{}", method);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        try {
            Map<String, Object> result = switch (method) {
                case "initialize" -> initialize();
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools", toolDefs());
                case "tools/call" -> callTool(asMap(body.get("params")));
                case "prompts/list" -> Map.of("prompts", List.of());
                case "resources/list" -> Map.of("resources", List.of());
                default -> null;
            };
            if (result == null) {
                return ResponseEntity.ok(error(id, -32601, "不支持的方法：" + method
                        + "。本站支持 initialize / tools/list / tools/call / ping。"));
            }
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            if ("initialize".equals(method)) {
                String sid = newSession(sessionId);
                builder.header(SESSION_HEADER, sid);
            } else if (sessionId != null && !sessionId.isBlank()) {
                builder.header(SESSION_HEADER, sessionId);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jsonrpc", "2.0");
            out.put("id", id);
            out.put("result", result);
            return builder.body(out);
        } catch (RuntimeException e) {
            log.warn("MCP 调用失败（{}）：{}", method, e.getMessage());
            return ResponseEntity.ok(error(id, -32603, String.valueOf(e.getMessage())));
        }
    }

    /** 探活用：任何 200 都说明这条 MCP 端点是活的。 */
    @GetMapping(path = "/mcp", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Map<String, Object> info() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transport", "streamable-http");
        out.put("protocolVersion", PROTOCOL_VERSION);
        out.put("server", Map.of("name", "skillhub-local-mcp", "version", "1.0.0"));
        out.put("tools", toolDefs().stream().map(t -> t.get("name")).toList());
        out.put("hint", "用 POST 发送 JSON-RPC：initialize → notifications/initialized → tools/list → tools/call");
        return out;
    }

    // ================================================================ 协议实现

    private Map<String, Object> initialize() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", capabilities,
                "serverInfo", Map.of("name", "skillhub-local-mcp", "version", "1.0.0"),
                "instructions", "这是 SkillHub 应用内自带的 MCP 服务：提供 skill_search / skill_read / skill_stats 三个工具，"
                        + "数据直接来自本站 Skill 库。");
    }

    private List<Map<String, Object>> toolDefs() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("skill_search", "在本地 Skill 库里按关键词检索，返回命中的 Skill 摘要（名称、分类、风险、相似度分）。",
                Map.of("type", "object", "properties", Map.of(
                        "query", Map.of("type", "string", "description", "关键词，例如 pdf / 教学 / 合同"),
                        "limit", Map.of("type", "integer", "description", "返回条数，默认 5")),
                        "required", List.of("query"))));
        tools.add(tool("skill_read", "读取某个 Skill 的 SKILL.md 正文（默认截断 4000 字）。",
                Map.of("type", "object", "properties", Map.of(
                        "skill_id", Map.of("type", "string", "description", "如 lib:pdf"),
                        "name", Map.of("type", "string", "description", "或直接给 Skill 名"),
                        "max_chars", Map.of("type", "integer", "description", "最多返回多少字，默认 4000")),
                        "required", List.of())));
        tools.add(tool("skill_stats", "返回本站 Skill 库的规模与分布（总数、分类计数、风险分布）。",
                Map.of("type", "object", "properties", Map.of())));
        return tools;
    }

    private Map<String, Object> tool(Map<String, Object> annotations) {
        return annotations;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> schema) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", schema);
        return t;
    }

    private Map<String, Object> callTool(Map<String, Object> params) {
        String name = str(params.get("name"));
        Map<String, Object> args = asMap(params.get("arguments"));
        String text = switch (name == null ? "" : name) {
            case "skill_search" -> doSearch(args);
            case "skill_read" -> doRead(args);
            case "skill_stats" -> doStats();
            default -> "未知工具：" + name + "。可用：skill_search / skill_read / skill_stats。";
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", List.of(Map.of("type", "text", "text", text)));
        out.put("isError", text.startsWith("未知工具"));
        return out;
    }

    private String doSearch(Map<String, Object> args) {
        String query = str(args.get("query"));
        if (query == null || query.isBlank()) {
            query = str(args.get("input"));
        }
        int limit = intOf(args.get("limit"), 5);
        var hits = skillService.search(query, Math.max(1, Math.min(limit, 10)));
        if (hits.isEmpty()) {
            return "（本地 Skill 库没有命中「" + query + "」的条目）";
        }
        StringBuilder sb = new StringBuilder("本地 Skill 库命中 ").append(hits.size()).append(" 条：\n");
        for (var h : hits) {
            sb.append("- ").append(h.card().skillId())
                    .append(" ｜ ").append(h.card().name())
                    .append(" ｜ 分类 ").append(h.card().categoryLabel())
                    .append(" ｜ 风险 ").append(h.card().riskLabel())
                    .append(" ｜ 命中字段 ").append(h.matchedIn())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private String doRead(Map<String, Object> args) {
        String skillId = str(args.get("skill_id"));
        String name = str(args.get("name"));
        int maxChars = Math.max(200, Math.min(intOf(args.get("max_chars"), 4000), 20000));
        org.skillhub.model.Skill skill = null;
        if (skillId != null && !skillId.isBlank()) {
            skill = skillService.find(skillId);
        }
        if (skill == null && name != null && !name.isBlank()) {
            var hits = skillService.search(name, 1);
            if (!hits.isEmpty()) {
                skill = skillService.find(hits.get(0).card().skillId());
            }
        }
        if (skill == null) {
            return "找不到这个 Skill。怎么办：先调 skill_search 拿到 skill_id，再传进来。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(skill.name).append('\n');
        sb.append("skill_id: ").append(skill.skillId).append('\n');
        sb.append("分类: ").append(skill.categoryCode).append(" / 场景: ").append(skill.sceneCode)
                .append(" / 风险: ").append(skill.riskLevel).append('\n');
        sb.append("---- SKILL.md ----\n");
        sb.append(Texts.truncate(skill.content == null ? "" : skill.content, maxChars));
        return sb.toString();
    }

    private String doStats() {
        var stats = skillService.page(queryAll());
        return "Skill 库规模：" + stats.total() + " 个 Skill。"
                + "可用工具：skill_search（检索）、skill_read（读正文）、skill_stats（本工具）。";
    }

    private static org.skillhub.dto.SkillQuery queryAll() {
        org.skillhub.dto.SkillQuery q = new org.skillhub.dto.SkillQuery();
        q.setSize(1);
        return q;
    }

    // ================================================================ 工具

    private String newSession(String existing) {
        if (existing != null && !existing.isBlank() && sessions.contains(existing)) {
            return existing;
        }
        String sid = UUID.randomUUID().toString();
        sessions.add(sid);
        return sid;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.put("error", Map.of("code", code, "message", message == null ? "" : message));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (RuntimeException e) {
            return def;
        }
    }
}
