package org.skillhub.adapter.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.port.McpPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * MCP 路由：内置服务走进程内实现，其余走 HTTP；失败可回退到内置实现。
 *
 * <p>回退时结果里 {@code offline=true} 且带上失败原因，页面会同时显示两件事：
 * "这次的结果来自内置兜底" + "为什么没走成真实服务"。
 */
@Component
@Primary
public class McpRouter implements McpPort {

    private final BuiltinMcpAdapter builtin;
    private final HttpMcpAdapter http;
    private final EvalMapper evalMapper;

    public McpRouter(BuiltinMcpAdapter builtin, HttpMcpAdapter http, EvalMapper evalMapper) {
        this.builtin = builtin;
        this.http = http;
        this.evalMapper = evalMapper;
    }

    @Override
    public Result call(Call call) {
        String code = call.mcpCode() == null ? "" : call.mcpCode();
        if (code.isBlank() || BuiltinMcpAdapter.CODE.equalsIgnoreCase(code)) {
            return builtin.call(call);
        }
        MsEntities.McpRecord rec = evalMapper.findMcp(code);
        if (rec == null || Boolean.FALSE.equals(rec.enabled)) {
            Result r = builtin.call(call);
            return new Result(r.ok(), r.content(), r.latencyMs(), true,
                    (rec == null ? "注册表里没有「" + code + "」" : "MCP「" + rec.name + "」已停用")
                            + "，已用内置工具兜底。");
        }
        Result real = http.call(call);
        if (real.ok()) {
            return real;
        }
        Result fallback = builtin.call(call);
        return new Result(fallback.ok(), fallback.content(),
                real.latencyMs() + fallback.latencyMs(), true, real.error());
    }

    @Override
    public Map<String, Object> probe(String mcpCode) {
        if (BuiltinMcpAdapter.CODE.equalsIgnoreCase(mcpCode)) {
            return builtin.probe(mcpCode);
        }
        Map<String, Object> out = new LinkedHashMap<>(http.probe(mcpCode));
        out.put("mode", "http");
        return out;
    }

    @Override
    public List<String> tools(String mcpCode) {
        if (BuiltinMcpAdapter.CODE.equalsIgnoreCase(mcpCode)) {
            return builtin.tools(mcpCode);
        }
        return http.tools(mcpCode);
    }
}
