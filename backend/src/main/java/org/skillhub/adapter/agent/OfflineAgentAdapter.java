package org.skillhub.adapter.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.skillhub.support.Metrics;
import org.skillhub.port.AgentPort;
import org.springframework.stereotype.Component;

/**
 * 离线智能体（{@code local-stub}）：不联网、确定性、可自动验收。
 *
 * <p>它<b>不是</b>"假装成功"：输出里明确写 {@code offline=true} 与一段固定格式的说明，
 * 而质量分照常按真实口径算（因为它确实生成了一段文本）。
 * 它的价值是让<b>整条链路</b>（选 Skill → 组装提示 → 调用 → 记录 → 评分 → 汇总）
 * 在没有网络的环境里也能被验证。
 *
 * <p>生成策略（确定性，同输入同输出）：
 * <ol>
 *   <li>推理过程：按"读手册 → 找关键步骤 → 组织答复"三步给出</li>
 *   <li>输出正文：摘出 Skill 说明里的前若干要点 + 回扣用户问题里的关键词</li>
 * </ol>
 */
@Component
public class OfflineAgentAdapter implements AgentPort {

    public static final String CODE = "local-stub";

    @Override
    public Reply invoke(Request request) {
        long start = System.currentTimeMillis();
        String skillName = extract(request.systemPrompt(), "【Skill 名称】");
        String skillDesc = extract(request.systemPrompt(), "【Skill 说明】");
        List<String> keyPoints = keyPoints(skillDesc, request.userInput());

        List<String> reasoning = new ArrayList<>();
        reasoning.add("1) 我拿到的是一份 Skill 手册「" + nz(skillName) + "」，它规定了我该按什么步骤做事。");
        reasoning.add("2) 用户的问题是：" + trim(request.userInput(), 80));
        reasoning.add("3) 手册里与这个问题相关的要点有 " + keyPoints.size() + " 条，我按顺序组织答复。");
        reasoning.add("4) 因为当前没有可用的外部模型（离线桩），我用手册要点直接拼出答复，并标注 offline。");

        StringBuilder sb = new StringBuilder();
        sb.append("【离线桩输出】当前没有连接到外部智能体，以下内容由手头手册整理而成，仅供参考。\n\n");
        if (!keyPoints.isEmpty()) {
            sb.append("根据《").append(nz(skillName)).append("》的手册，可以这样做：\n");
            for (int i = 0; i < keyPoints.size(); i++) {
                sb.append(i + 1).append(". ").append(keyPoints.get(i)).append('\n');
            }
        } else {
            sb.append("手册里没有提到与问题直接相关的步骤。建议先补充手册内容再重试。\n");
        }
        sb.append("\n完成标准：按上面步骤逐条执行，并检查每一步的结果是否符合预期。");

        int latency = (int) (System.currentTimeMillis() - start) + 40 + (request.userInput().length() % 60);
        int completion = Math.max(1, Metrics.tokens(sb.toString()).size());
        return new Reply(true, sb.toString(), String.join("\n", reasoning),
                latency, Math.max(20, latency / 3),
                Math.max(1, Metrics.tokens(request.systemPrompt()).size()), completion,
                true, "", List.of());
    }

    @Override
    public java.util.Map<String, Object> probe(String agentCode) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("ok", true);
        out.put("latency_ms", 1);
        out.put("status_code", 200);
        out.put("detail", "离线桩：进程内实现，永远可用（不产生任何网络调用）");
        return out;
    }

    // ------------------------------------------------------------------ 内部

    /** 从系统提示里抽出某个标记段的内容（到下一个【为止）。 */
    private static String extract(String prompt, String tag) {
        if (prompt == null) {
            return "";
        }
        int i = prompt.indexOf(tag);
        if (i < 0) {
            return "";
        }
        int start = i + tag.length();
        int end = prompt.indexOf('【', start);
        String v = end < 0 ? prompt.substring(start) : prompt.substring(start, end);
        return v.replaceAll("\\s+", " ").trim();
    }

    /** 把说明与问题里的要点挑出来（按句子切，去掉过短的）。 */
    private static List<String> keyPoints(String desc, String question) {
        List<String> out = new ArrayList<>();
        String hay = (desc == null ? "" : desc) + "。" + (question == null ? "" : question);
        for (String raw : hay.split("[。；;\\n]")) {
            String s = raw.replaceAll("\\s+", " ").trim();
            if (s.length() >= 8 && s.length() <= 120 && !s.equals(question)) {
                out.add(s);
            }
            if (out.size() >= 4) {
                break;
            }
        }
        if (out.isEmpty()) {
            String q = question == null ? "" : question.trim();
            if (!q.isEmpty()) {
                out.add("先明确问题范围：" + trim(q, 60));
                out.add("再按手册给出的步骤逐条执行并核对结果");
            }
        }
        return out;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "（未命名）" : s;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** 离线桩的固定 code（供注册表与页面使用）。 */
    public static String code() {
        return CODE.toLowerCase(Locale.ROOT);
    }
}
