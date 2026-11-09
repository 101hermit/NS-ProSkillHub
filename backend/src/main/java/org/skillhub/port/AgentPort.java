package org.skillhub.port;

import java.util.List;
import java.util.Map;

/**
 * 智能体调用端口。
 *
 * <p>实现必须自带超时、必须把技术异常翻译成可读原因、必须返回 {@code offline} 标记。
 */
public interface AgentPort {

    /**
     * 一次调用请求。
     *
     * @param agentCode  注册表里的 code（
     *                   {@code local-stub} 走离线实现）
     * @param systemPrompt 系统提示（里含 Skill 正文 + 场景约束）
     * @param userInput  用户问题
     * @param params     超参数：temperature / top_p / max_tokens
     */
    record Request(String agentCode, String systemPrompt, String userInput,
                   Map<String, Double> params, int maxTokens) {
    }

    /**
     * 一次调用结果。
     *
     * @param ok         是否成功
     * @param output     模型输出正文
     * @param reasoning  推理过程（模型显式给出的"打算怎么做"；离线实现会生成步骤化文本）
     * @param latencyMs  端到端耗时
     * @param ttftMs     首字耗时（拿不到时等于 latencyMs）
     * @param promptTokens / completionTokens 用量
     * @param offline    是否走了本地兜底
     * @param error      失败原因（人话）
     */
    record Reply(boolean ok, String output, String reasoning, int latencyMs, int ttftMs,
                 int promptTokens, int completionTokens, boolean offline, String error,
                 List<String> toolCalls) {
    }

    /** 调用一个已注册的智能体。 */
    Reply invoke(Request request);

    /**
     * 探活：只要能拿到任何一个 HTTP 响应就算"通"（不花 token）。
     *
     * @return {ok, latencyMs, statusCode, detail}
     */
    Map<String, Object> probe(String agentCode);
}
