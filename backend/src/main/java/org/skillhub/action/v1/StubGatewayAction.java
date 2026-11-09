package org.skillhub.action.v1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.skillhub.business.SkillService;
import org.skillhub.support.Metrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * **应用内自带的 OpenAI 兼容网关**（"协议锚点"）。
 *
 * <p>它存在的唯一目的：让「Agent 接入」这件事**在任何环境都能被真实验证**。
 *
 * <p>内网的现实是：外网模型要么没 key、要么网络不通、要么配额用光；
 * 于是"接入测试"最后总是失败，链路也就没人敢信。这里把同一个应用的一个端点
 * 伪装成一个标准的 OpenAI 兼容服务：
 *
 * <pre>
 *   GET  /stub/v1/models              → 模型清单
 *   POST /stub/v1/chat/completions    → 标准补全响应（含 reasoning_content）
 * </pre>
 *
 * <p>它的回复是**确定性**的（同样的输入给同样的输出），因此：
 * <ul>
 *   <li>可以用来验证"能不能连、超时多少、协议字段对不对"；</li>
 *   <li>可以用来跑实验矩阵（结果可复现，不会因为模型抖动而结论漂移）；</li>
 *   <li>页面会明确标注它是 <b>stub</b>，不会被误当成真实模型的效果。</li>
 * </ul>
 *
 * <p>真实模型（DeepSeek / 智谱 / 方舟 / 百炼）走的是完全相同的 {@code /chat/completions} 形状 ——
 * 这就是"一套适配器打天下"的原因。
 */
@RestController
@RequestMapping("/stub/v1")
public class StubGatewayAction {

    private final SkillService skillService;

    public StubGatewayAction(SkillService skillService) {
        this.skillService = skillService;
    }

    /** 模型清单：与 OpenAI 的 {@code /v1/models} 同形。 */
    @GetMapping("/models")
    public Map<String, Object> models() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of("id", "skillhub-stub", "object", "model", "owned_by", "skillhub-local"));
        data.add(Map.of("id", "skillhub-stub-fast", "object", "model", "owned_by", "skillhub-local"));
        return Map.of("object", "list", "data", data);
    }

    /** 标准补全响应：{@code choices[0].message.content} + {@code reasoning_content} + {@code usage}。 */
    @PostMapping("/chat/completions")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        long start = System.currentTimeMillis();
        String model = str(body.get("model"), "skillhub-stub");
        String userText = lastUserText(body);
        String systemText = firstSystemText(body);
        int maxTokens = intOf(body.get("max_tokens"), 512);

        String reasoning = reasoningOf(systemText, userText);
        String content = answerOf(systemText, userText, maxTokens);
        int promptTokens = Math.max(1, Metrics.tokens(systemText + "\n" + userText).size());
        int completionTokens = Math.max(1, Metrics.tokens(content).size());
        int elapsed = (int) (System.currentTimeMillis() - start);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        // 推理过程：DeepSeek/GLM 等推理模型用这个字段；不带它的客户端会忽略
        message.put("reasoning_content", reasoning);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        choice.put("logprobs", null);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", "chatcmpl-stub-" + UUID.randomUUID().toString().substring(0, 8));
        out.put("object", "chat.completion");
        out.put("created", System.currentTimeMillis() / 1000);
        out.put("model", model);
        out.put("choices", List.of(choice));
        out.put("usage", usage);
        // 自定义标记：让页面能一眼看出"这次是 stub 回的"
        out.put("x_stub", Map.of("gateway", "skillhub-local", "elapsed_ms", elapsed,
                "note", "这是应用内自带的协议锚点，输出为确定性文本，不代表真实模型效果"));
        return out;
    }

    // ================================================================ 生成逻辑

    /** 推理过程：固定三步，让"推理链"这个产品概念在任何环境都能被演示。 */
    private static String reasoningOf(String system, String user) {
        String skillName = between(system, "【Skill 名称】", "【");
        List<String> steps = new ArrayList<>();
        steps.add("1) 我要先确认手上有没有 Skill 手册：" + (skillName.isBlank() ? "没有，按通用知识回答。" : "有《" + skillName + "》。"));
        steps.add("2) 再判断用户到底在问什么：" + trim(user, 60));
        steps.add("3) 最后按要求组织答复（先步骤、再结论），并核对是否覆盖了用户的关键词。");
        return String.join("\n", steps);
    }

    /** 答复：确定性地拼出"按手册做的步骤"，并回扣用户问题里的关键词。 */
    private String answerOf(String system, String user, int maxTokens) {
        String skillName = between(system, "【Skill 名称】", "【");
        String skillDesc = between(system, "【Skill 说明】", "【");
        List<String> points = new ArrayList<>();
        for (String raw : (skillDesc + "。" + user).split("[。；;\\n]")) {
            String s = raw.replaceAll("\\s+", " ").trim();
            if (s.length() >= 6 && s.length() <= 100) {
                points.add(s);
            }
            if (points.size() >= 3) {
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【协议锚点输出】当前回答来自站点自带的 OpenAI 兼容网关（stub），用于验证接入链路，")
                .append("不代表真实模型效果。\n\n");
        if (!skillName.isBlank()) {
            sb.append("依据《").append(skillName).append("》的手册，建议这样做：\n");
        } else {
            sb.append("建议这样做：\n");
        }
        if (points.isEmpty()) {
            sb.append("1. 先明确目标与输入范围；\n2. 按最小步骤执行并核对每一步结果；\n3. 汇总为一个可交付的结论。\n");
        } else {
            for (int i = 0; i < points.size(); i++) {
                sb.append(i + 1).append(". ").append(points.get(i)).append('\n');
            }
        }
        sb.append("\n完成标准：上面每一步都能指出对应的输入或产出，不靠猜测。");
        return trim(sb.toString(), Math.max(120, maxTokens * 3));
    }

    // ================================================================ 解析

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messages(Map<String, Object> body) {
        Object raw = body.get("messages");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }

    private static String lastUserText(Map<String, Object> body) {
        String last = "";
        for (Map<String, Object> m : messages(body)) {
            if ("user".equals(str(m.get("role"), ""))) {
                last = str(m.get("content"), "");
            }
        }
        return last;
    }

    private static String firstSystemText(Map<String, Object> body) {
        for (Map<String, Object> m : messages(body)) {
            if ("system".equals(str(m.get("role"), ""))) {
                return str(m.get("content"), "");
            }
        }
        return "";
    }

    private static String between(String text, String open, String nextTag) {
        if (text == null) {
            return "";
        }
        int i = text.indexOf(open);
        if (i < 0) {
            return "";
        }
        int start = i + open.length();
        int end = text.indexOf(nextTag, start);
        String v = end < 0 ? text.substring(start) : text.substring(start, end);
        return v.replaceAll("\\s+", " ").trim();
    }

    private static String str(Object o, String def) {
        if (o == null) {
            return def;
        }
        String s = String.valueOf(o);
        return s.isBlank() ? def : s;
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

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
