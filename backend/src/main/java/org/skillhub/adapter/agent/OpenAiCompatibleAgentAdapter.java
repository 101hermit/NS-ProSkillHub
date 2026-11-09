package org.skillhub.adapter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.config.NetEgress;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.port.AgentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容接口的智能体适配器（DeepSeek / 智谱 / 火山方舟 / 千帆 / DashScope 都走这一套）。
 *
 * <p>职责：协议转换 + 超时 + 错误归因。**不做**业务判断（不做评分、不改写输出）。
 *
 * <p>关键实现细节：
 * <ul>
 *   <li>推理过程（{@code reasoning_content}）与正文（{@code content}）分开放——
 *       页面要展示"它打算怎么做"和"它最后说了什么"两件事</li>
 *   <li>首字耗时（TTFT）用"请求发出 → 收到完整响应"的近似值；流式留作后续路线并已登记</li>
 *   <li>失败一律翻译成 {@link ErrorCode#AGENT_UNAVAILABLE} 或 {@link ErrorCode#EGRESS_BLOCKED}，
 *       带"怎么办"</li>
 * </ul>
 */
@Component
public class OpenAiCompatibleAgentAdapter implements AgentPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAgentAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EvalMapper evalMapper;
    private final NetEgress egress;

    public OpenAiCompatibleAgentAdapter(EvalMapper evalMapper, NetEgress egress) {
        this.evalMapper = evalMapper;
        this.egress = egress;
    }

    @Override
    public Reply invoke(Request request) {
        MsEntities.AgentRecord rec = evalMapper.findAgent(request.agentCode());
        if (rec == null) {
            return new Reply(false, "", "", 0, 0, 0, 0, false,
                    "注册表里没有这个智能体（" + request.agentCode() + "）。怎么办：先到「接入管理」里登记。",
                    List.of());
        }
        String apiKey = resolveKey(rec.apiKey);
        if (apiKey.isBlank()) {
            return new Reply(false, "", "", 0, 0, 0, 0, false,
                    "智能体「" + rec.name + "」缺少 API Key。怎么办：设置环境变量 "
                            + rec.apiKey + "，或在注册表里直接填 Key。", List.of());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", rec.model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userInput())));
        body.put("temperature", request.params().getOrDefault("temperature",
                rec.temperature == null ? 0.3 : rec.temperature.doubleValue()));
        body.put("top_p", request.params().getOrDefault("top_p",
                rec.topP == null ? 1.0 : rec.topP.doubleValue()));
        body.put("max_tokens", request.maxTokens() > 0 ? request.maxTokens()
                : (rec.maxTokens == null ? 2048 : rec.maxTokens));

        long start = System.currentTimeMillis();
        try {
            String url = trimSlash(rec.baseUrl) + "/chat/completions";
            HttpClient client = egress.newClientFor(url, Duration.ofMillis(90_000));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(90_000))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int elapsed = (int) (System.currentTimeMillis() - start);

            if (resp.statusCode() / 100 != 2) {
                String hint = hintFor(resp.statusCode());
                return new Reply(false, "", "", elapsed, elapsed, 0, 0, false,
                        "智能体「" + rec.name + "」返回 HTTP " + resp.statusCode() + "：" + shorten(resp.body())
                                + "。怎么办：" + hint, List.of());
            }
            JsonNode node = MAPPER.readTree(resp.body());
            JsonNode choice = node.path("choices").path(0).path("message");
            String content = choice.path("content").asText("");
            String reasoning = firstNonBlank(
                    choice.path("reasoning_content").asText(""),
                    choice.path("reasoning").asText(""));
            int promptTokens = node.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = node.path("usage").path("completion_tokens").asInt(0);
            if (content.isBlank() && reasoning.isBlank()) {
                return new Reply(false, "", "", elapsed, elapsed, promptTokens, completionTokens, false,
                        "模型返回了空内容。怎么办：调大 max_tokens，或换一个模型。", List.of());
            }
            return new Reply(true, content, reasoning, elapsed, elapsed,
                    promptTokens, completionTokens, false, "", List.of());
        } catch (java.net.http.HttpConnectTimeoutException e) {
            return fail(start, ErrorCode.EGRESS_BLOCKED,
                    "连接超时", "proxy.neusoft.com:8080 或网络不可达", "跑 scripts\\egress-doctor.ps1 看代理是否可用");
        } catch (java.net.UnknownHostException e) {
            return fail(start, ErrorCode.EGRESS_BLOCKED, "域名解析失败", e.getMessage(),
                    "确认当前是内网还是有线/无线网，并检查 DNS");
        } catch (javax.net.ssl.SSLHandshakeException e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("PKIX")) {
                return fail(start, ErrorCode.EGRESS_BLOCKED, "证书校验失败", msg,
                        "把公司根证书放进 config/certs/ 后重启（代理做了 TLS 解密）");
            }
            return fail(start, ErrorCode.EGRESS_BLOCKED, "TLS 握手被中断", msg,
                    "很可能没走代理：设置 HTTPS_PROXY=http://proxy.neusoft.com:8080");
        } catch (java.io.IOException e) {
            return fail(start, ErrorCode.AGENT_UNAVAILABLE, "网络异常", describeCause(e),
                    "稍后重试；持续失败请看 docs/05 §4 的四类归因表");
        } catch (IllegalArgumentException e) {
            // 例如 baseUrl 为空 → URI.create 抛 IllegalArgumentException，不能让它变成 500
            return fail(start, ErrorCode.INVALID_PARAM, "智能体地址不合法", String.valueOf(e.getMessage()),
                    "到「接入管理」把这个智能体的 base_url 补全（例如 https://api.deepseek.com/v1）");
        } catch (RuntimeException e) {
            return fail(start, ErrorCode.UPSTREAM_FAILED, "调用异常", String.valueOf(e.getMessage()),
                    "检查该智能体的 base_url / model / api_key 是否完整");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(start, ErrorCode.UPSTREAM_TIMEOUT, "调用被中断", e.getMessage(), "重试一次");
        }
    }

    @Override
    public Map<String, Object> probe(String agentCode) {
        MsEntities.AgentRecord rec = evalMapper.findAgent(agentCode);
        Map<String, Object> out = new LinkedHashMap<>();
        if (rec == null) {
            out.put("ok", false);
            out.put("detail", "注册表里没有 " + agentCode);
            return out;
        }
        long start = System.currentTimeMillis();
        try {
            String probeUrl = trimSlash(rec.baseUrl) + "/models";
            HttpClient client = egress.newClientFor(probeUrl, Duration.ofMillis(10_000));
            // 探活用 /models：轻量、不花 token、任何 2xx/4xx 都说明"通道是通的"
            HttpRequest req = HttpRequest.newBuilder(URI.create(probeUrl))
                    .timeout(Duration.ofMillis(10_000))
                    .header("Authorization", "Bearer " + resolveKey(rec.apiKey))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int ms = (int) (System.currentTimeMillis() - start);
            boolean ok = resp.statusCode() < 500;
            out.put("ok", ok);
            out.put("latency_ms", ms);
            out.put("status_code", resp.statusCode());
            out.put("detail", ok ? "通道可达（HTTP " + resp.statusCode() + "）"
                    : "服务端错误 HTTP " + resp.statusCode());
            evalMapper.markAgentProbe(agentCode, ok ? "ok" : "error", ms,
                    ok ? "" : shorten(resp.body()));
            return out;
        } catch (Exception e) {
            int ms = (int) (System.currentTimeMillis() - start);
            String reason = describe(e);
            out.put("ok", false);
            out.put("latency_ms", ms);
            out.put("detail", reason);
            evalMapper.markAgentProbe(agentCode, "error", ms, reason);
            return out;
        }
    }

    // ------------------------------------------------------------------ 内部

    private Reply fail(long start, ErrorCode code, String what, String detail, String hint) {
        int ms = (int) (System.currentTimeMillis() - start);
        log.warn("智能体调用失败：{} / {} / {}", what, detail, hint);
        return new Reply(false, "", "", ms, ms, 0, 0, false,
                code.format(what + "：" + shorten(detail)) + "怎么办：" + hint, List.of());
    }

    private static String hintFor(int status) {
        return switch (status) {
            case 401, 403 -> "API Key 无效或没权限，换一个有效 Key";
            case 404 -> "baseUrl 或 model 名不对，检查注册表";
            case 429 -> "触发限流，等一会儿再试或降低并发";
            default -> status >= 500 ? "上游服务异常，稍后重试" : "检查请求参数";
        };
    }

    private static String describe(Exception e) {
        if (e instanceof java.net.http.HttpConnectTimeoutException) {
            return "连接代理/服务超时。怎么办：跑 scripts\\egress-doctor.ps1 检查代理";
        }
        if (e instanceof java.net.ConnectException) {
            // 最常见的一种：目标端口没人监听。以前这里会显示 "网络异常：null"，
            // 用户完全无法判断是"某台机器没起"还是"端口写错了"。
            return "连接被拒绝（目标没在监听）。怎么办：确认那个服务的地址与端口，"
                    + "本站自带的 MCP/网关应指向本站自己的端口。原始信息：" + describeCause(e);
        }
        if (e instanceof javax.net.ssl.SSLHandshakeException) {
            String m = String.valueOf(e.getMessage());
            return m.contains("PKIX")
                    ? "证书校验失败（缺公司根证书）。怎么办：把根证书放进 config/certs/"
                    : "TLS 握手被中断（很可能没走代理）。怎么办：设置 HTTPS_PROXY";
        }
        if (e instanceof java.net.UnknownHostException) {
            return "域名解析失败。怎么办：检查网络/DNS";
        }
        return "网络异常：" + describeCause(e);
    }

    /**
     * 把异常渲染成一句能看懂的话。
     *
     * <p>为什么要单独做：{@code e.getMessage()} 经常是 {@code null}（被包装过的 IOException、
     * 某些 ConnectException），直接拼字符串就会得到 "网络异常：null" —— 这等于没说。
     * 至少要把异常类型与 cause 报出来，用户才有可能自己定位。
     */
    private static String describeCause(Throwable e) {
        if (e == null) {
            return "未知异常";
        }
        StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
        String m = e.getMessage();
        if (m != null && !m.isBlank()) {
            sb.append('：').append(m);
        }
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            sb.append(" ← ").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                sb.append('：').append(cause.getMessage());
            }
        }
        return sb.toString();
    }

    private static String resolveKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.startsWith("env:")) {
            String v = System.getenv(raw.substring(4));
            return v == null ? "" : v;
        }
        return raw;
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 300 ? t : t.substring(0, 300) + "…";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    /** 供其它适配器复用：拼 OpenAI 兼容的消息体。 */
    public static List<Map<String, String>> messages(String system, String user) {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(Map.of("role", "system", "content", system));
        out.add(Map.of("role", "user", "content", user));
        return out;
    }

    /** 供探活页展示当前时刻。 */
    public static Instant now() {
        return Instant.now();
    }

    /** 把异常包装成业务异常（少数需要抛的场景）。 */
    public static ApiException upstream(String msg) {
        return ApiException.ofMessage(ErrorCode.UPSTREAM_FAILED, msg);
    }
}
