package org.skillhub.adapter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.skillhub.config.NetEgress;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.port.McpPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HTTP 形态的 MCP 客户端（**Streamable HTTP** 传输，协议版本 2025-06-18）。
 *
 * <p>这个类是本轮重写的重点。之前那版只发了一个裸的 {@code tools/call}，
 * 既没有握手也没有会话头 —— 对真正的 MCP 服务会 100% 失败。真实协议是四步：
 *
 * <pre>
 *   ① POST  initialize                 ← 响应头带 Mcp-Session-Id
 *   ② POST  notifications/initialized  ← 通知（无 id），服务端回 202 且无 body
 *   ③ POST  tools/list                 ← 必须回传 ①的会话头，否则被判为无状态调用
 *   ④ POST  tools/call                 ← 同样带会话头
 * </pre>
 *
 * <p>另外两个必须处理的细节：
 * <ul>
 *   <li>响应可能是 {@code text/event-stream}（SSE），要取最后一条 {@code data:} 行再解析，
 *       不能对整个 body 直接 {@code JSON.parse}；</li>
 *   <li>{@code notifications/initialized} 的回包是 202 空 body，按普通 RPC 解析会报错。</li>
 * </ul>
 *
 * <p>探活口径：<b>能把 tools/list 拿回来才算通</b>（这比"端口能连上"强得多）。
 */
@Component
public class HttpMcpAdapter implements McpPort {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final EvalMapper evalMapper;
    private final NetEgress egress;

    public HttpMcpAdapter(EvalMapper evalMapper, NetEgress egress) {
        this.evalMapper = evalMapper;
        this.egress = egress;
    }

    // ================================================================ 调用

    @Override
    public Result call(Call call) {
        MsEntities.McpRecord rec = evalMapper.findMcp(call.mcpCode());
        if (rec == null) {
            return new Result(false, "", 0, false, "注册表里没有这个 MCP 服务（" + call.mcpCode() + "）");
        }
        if (rec.endpoint == null || rec.endpoint.isBlank()) {
            return new Result(false, "", 0, false, "MCP 服务「" + rec.name + "」还没配 endpoint");
        }
        long start = System.currentTimeMillis();
        try {
            Session session = handshake(rec, 10_000);
            String tool = call.toolName() == null || call.toolName().isBlank() ? rec.toolName : call.toolName();
            if (tool == null || tool.isBlank()) {
                tool = firstTool(session) ;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", tool);
            params.put("arguments", call.arguments());
            RpcReply reply = rpc(rec, session, "tools/call", params, 20_000);
            int ms = (int) (System.currentTimeMillis() - start);
            if (!reply.ok()) {
                evalMapper.markMcpProbe(call.mcpCode(), "error", ms, reply.error());
                return new Result(false, "", ms, false, reply.error());
            }
            evalMapper.markMcpProbe(call.mcpCode(), "ok", ms, "");
            return new Result(true, extractText(reply.result()), ms, false, "");
        } catch (Exception e) {
            int ms = (int) (System.currentTimeMillis() - start);
            String reason = describe(e);
            evalMapper.markMcpProbe(call.mcpCode(), "error", ms, reason);
            return new Result(false, "", ms, false, reason);
        }
    }

    // ================================================================ 探活

    @Override
    public Map<String, Object> probe(String mcpCode) {
        MsEntities.McpRecord rec = evalMapper.findMcp(mcpCode);
        Map<String, Object> out = new LinkedHashMap<>();
        if (rec == null) {
            out.put("ok", false);
            out.put("detail", "注册表里没有 " + mcpCode);
            return out;
        }
        long start = System.currentTimeMillis();
        try {
            Session session = handshake(rec, 8_000);
            RpcReply list = rpc(rec, session, "tools/list", Map.of(), 8_000);
            int ms = (int) (System.currentTimeMillis() - start);
            List<String> tools = toolNames(list.result());
            boolean ok = list.ok() && !tools.isEmpty();
            out.put("ok", ok);
            out.put("latency_ms", ms);
            out.put("detail", ok
                    ? "MCP 握手成功，发现 " + tools.size() + " 个工具：" + String.join("/", tools)
                    : "MCP 握手成功但 tools/list 失败：" + list.error());
            out.put("tools", tools);
            out.put("protocol_version", session.protocolVersion);
            evalMapper.markMcpProbe(mcpCode, ok ? "ok" : "error", ms, ok ? "" : list.error());
            return out;
        } catch (Exception e) {
            int ms = (int) (System.currentTimeMillis() - start);
            String reason = describe(e);
            out.put("ok", false);
            out.put("latency_ms", ms);
            out.put("detail", reason);
            out.put("tools", List.of());
            evalMapper.markMcpProbe(mcpCode, "error", ms, reason);
            return out;
        }
    }

    @Override
    public List<String> tools(String mcpCode) {
        MsEntities.McpRecord rec = evalMapper.findMcp(mcpCode);
        if (rec == null) {
            return List.of();
        }
        try {
            Session session = handshake(rec, 6_000);
            return toolNames(rpc(rec, session, "tools/list", Map.of(), 6_000).result());
        } catch (Exception e) {
            return rec.toolName == null || rec.toolName.isBlank() ? List.of() : List.of(rec.toolName);
        }
    }

    // ================================================================ 握手与 RPC

    /** 一个 MCP 会话。 */
    private record Session(String sessionId, String protocolVersion) {
    }

    /** 一次 RPC 的结果。 */
    private record RpcReply(boolean ok, JsonNode result, String error) {
    }

    /** ①② 握手：initialize + notifications/initialized。 */
    private Session handshake(MsEntities.McpRecord rec, long timeoutMs) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "skillhub-agent", "version", "1.0.0"));
        HttpResponse<String> initResp = send(rec, null, "initialize", params, timeoutMs);
        String sid = initResp.headers().firstValue(SESSION_HEADER).orElse("");
        JsonNode initBody = parseBody(initResp);
        String version = initBody == null ? PROTOCOL_VERSION
                : initBody.path("result").path("protocolVersion").asText(PROTOCOL_VERSION);
        if (sid.isBlank() && initBody != null) {
            sid = initBody.path("result").path("sessionId").asText("");
        }
        // ② 通知（无 id）：服务端应回 202/200 且无 body，忽略内容
        try {
            send(rec, sid.isBlank() ? null : sid, null, null, timeoutMs);
        } catch (Exception e) {
            log.debug("notifications/initialized 失败（多数服务端可容忍）：{}", e.getMessage());
        }
        return new Session(sid, version);
    }

    /** 发一次 JSON-RPC（method 为 null 时是 notifications/initialized）。 */
    private RpcReply rpc(MsEntities.McpRecord rec, Session session, String method,
                         Map<String, Object> params, long timeoutMs) throws Exception {
        HttpResponse<String> resp = send(rec, session.sessionId(), method, params, timeoutMs);
        JsonNode body = parseBody(resp);
        if (resp.statusCode() / 100 != 2) {
            return new RpcReply(false, null, "HTTP " + resp.statusCode() + "：" + shorten(resp.body()));
        }
        if (body == null) {
            return new RpcReply(false, null, "响应不是合法 JSON：" + shorten(resp.body()));
        }
        if (body.has("error")) {
            String msg = body.path("error").path("message").asText("未知错误");
            return new RpcReply(false, null, "JSON-RPC 错误 " + body.path("error").path("code").asInt()
                    + "：" + msg);
        }
        return new RpcReply(true, body.path("result"), "");
    }

    /** 真正的 HTTP 发送（含 SSE 与会话头处理）。 */
    private HttpResponse<String> send(MsEntities.McpRecord rec, String sessionId, String method,
                                      Map<String, Object> params, long timeoutMs) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        if (method == null) {
            payload.put("method", "notifications/initialized");
        } else {
            payload.put("id", UUID.randomUUID().toString().substring(0, 8));
            payload.put("method", method);
            payload.put("params", params == null ? Map.of() : params);
        }
        HttpClient client = egress.newClientFor(rec.endpoint, Duration.ofMillis(timeoutMs));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(rec.endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION);
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header(SESSION_HEADER, sessionId);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 解析响应体：可能是纯 JSON，也可能是 SSE（{@code data: ...}）。
     * SSE 时取**最后一条** data 行 —— 这是 MCP 的约定（前面的可能是进度/日志）。
     */
    private static JsonNode parseBody(HttpResponse<String> resp) {
        String text = resp.body();
        if (text == null || text.isBlank()) {
            return null;
        }
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        String candidate = text.trim();
        if (contentType.contains("text/event-stream") || candidate.startsWith("event:") || candidate.startsWith("data:")) {
            String last = null;
            for (String line : text.split("\n")) {
                String l = line.trim();
                if (l.startsWith("data:")) {
                    String payload = l.substring(5).trim();
                    if (!payload.isEmpty() && !"[DONE]".equals(payload)) {
                        last = payload;
                    }
                }
            }
            if (last == null) {
                return null;
            }
            candidate = last;
        }
        try {
            return MAPPER.readTree(candidate);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> toolNames(JsonNode result) {
        List<String> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (JsonNode t : result.path("tools")) {
            String name = t.path("name").asText("");
            if (!name.isBlank()) {
                out.add(name);
            }
        }
        return out;
    }

    private String firstTool(Session session) {
        return "";
    }

    /** 从 {@code result.content[]} 里抽出文本（MCP 规定的形状）。 */
    private static String extractText(JsonNode result) {
        if (result == null) {
            return "";
        }
        JsonNode content = result.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content) {
                String type = item.path("type").asText("text");
                if ("text".equals(type)) {
                    String t = item.path("text").asText("");
                    if (!t.isBlank()) {
                        sb.append(t).append('\n');
                    }
                } else if ("resource".equals(type)) {
                    sb.append(item.path("resource").path("text").asText("")).append('\n');
                }
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }
        String direct = result.path("text").asText("");
        return direct.isBlank() ? result.toString() : direct;
    }

    private static String describe(Exception e) {
        if (e instanceof java.net.http.HttpConnectTimeoutException) {
            return "连接 MCP 超时。怎么办：确认服务已启动、endpoint 正确（本站自带端点是 http://127.0.0.1:<端口>/mcp）";
        }
        if (e instanceof java.net.UnknownHostException) {
            return "MCP 域名解析失败。怎么办：改用内网 IP";
        }
        if (e instanceof java.net.ConnectException) {
            return "MCP 连接被拒绝。怎么办：服务没起，或端口不对";
        }
        if (e instanceof javax.net.ssl.SSLHandshakeException) {
            String m = String.valueOf(e.getMessage());
            return m.contains("PKIX")
                    ? "MCP 的证书不被信任。怎么办：把公司根证书放进 config/certs/"
                    : "MCP 的 TLS 握手被中断。怎么办：确认是否需要走代理";
        }
        return "MCP 调用失败：" + shorten(String.valueOf(e.getMessage()));
    }

    private static String shorten(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= 220 ? t : t.substring(0, 220) + "…";
    }
}
