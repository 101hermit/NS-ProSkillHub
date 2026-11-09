package org.skillhub.adapter.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.port.AgentPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 智能体路由：决定这一次调用走真实模型还是走离线桩。
 *
 * <p>决策顺序：
 * <ol>
 *   <li>{@code code = local-stub} → 直接离线桩（不尝试联网）</li>
 *   <li>注册表里 {@code enabled = false} → 离线桩，并说明"被停用"</li>
 *   <li>否则调真实模型；若失败且允许回退 → 用离线桩兜底，
 *       并把失败原因保留在 {@code error} 里（页面上会同时看到"离线结果"与"为什么离线"）</li>
 * </ol>
 *
 * <p>为什么默认允许回退：断网环境（内网第一次联调）下，如果不回退，
 * 整个"实测"功能区会变成一片红。回退让链路可用，同时不掩盖问题。
 */
@Component
@Primary
public class AgentRouter implements AgentPort {

    private final OfflineAgentAdapter offline;
    private final OpenAiCompatibleAgentAdapter real;
    private final EvalMapper evalMapper;

    public AgentRouter(OfflineAgentAdapter offline, OpenAiCompatibleAgentAdapter real,
                       EvalMapper evalMapper) {
        this.offline = offline;
        this.real = real;
        this.evalMapper = evalMapper;
    }

    @Override
    public Reply invoke(Request request) {
        String code = request.agentCode() == null ? "" : request.agentCode();
        if (OfflineAgentAdapter.CODE.equalsIgnoreCase(code)) {
            return offline.invoke(request);
        }
        MsEntities.AgentRecord rec = evalMapper.findAgent(code);
        if (rec == null) {
            Reply r = offline.invoke(request);
            return new Reply(r.ok(), r.output(), r.reasoning(), r.latencyMs(), r.ttftMs(),
                    r.promptTokens(), r.completionTokens(), true,
                    "注册表里没有「" + code + "」，已用离线桩兜底。怎么办：到「接入管理」登记这个智能体。",
                    r.toolCalls());
        }
        if (Boolean.FALSE.equals(rec.enabled)) {
            Reply r = offline.invoke(request);
            return new Reply(r.ok(), r.output(), r.reasoning(), r.latencyMs(), r.ttftMs(),
                    r.promptTokens(), r.completionTokens(), true,
                    "智能体「" + rec.name + "」已被停用，已用离线桩兜底。", r.toolCalls());
        }
        Reply reply = real.invoke(request);
        if (reply.ok()) {
            return reply;
        }
        // 真实调用失败 → 离线兜底，但把原因带上
        Reply fallback = offline.invoke(request);
        return new Reply(fallback.ok(), fallback.output(), fallback.reasoning(),
                reply.latencyMs() + fallback.latencyMs(), fallback.ttftMs(),
                fallback.promptTokens(), fallback.completionTokens(), true,
                reply.error(), fallback.toolCalls());
    }

    @Override
    public Map<String, Object> probe(String agentCode) {
        if (OfflineAgentAdapter.CODE.equalsIgnoreCase(agentCode)) {
            return offline.probe(agentCode);
        }
        Map<String, Object> out = new LinkedHashMap<>(real.probe(agentCode));
        out.put("mode", "real");
        return out;
    }
}
