package org.skillhub.port;

import java.util.List;
import java.util.Map;

/**
 * MCP（Model Context Protocol）服务端口。
 *
 * <p>标准版只接一个 MCP 服务（默认 {@code kb-search}：内网知识库检索），
 * 并额外内置一个进程内实现 {@code builtin-calc} 作为断网兜底。
 */
public interface McpPort {

    /** 一次工具调用。 */
    record Call(String mcpCode, String toolName, Map<String, Object> arguments) {
    }

    /**
     * 一次工具返回。
     *
     * @param ok      是否成功
     * @param content 工具返回的文本（会拼进模型的上下文）
     * @param latencyMs 调用耗时
     * @param offline 是否走了内置兜底
     * @param error   失败原因（人话）
     */
    record Result(boolean ok, String content, int latencyMs, boolean offline, String error) {
    }

    /** 调用一个已注册的 MCP 工具。 */
    Result call(Call call);

    /** 探活。 */
    Map<String, Object> probe(String mcpCode);

    /** 列出某个 MCP 服务暴露的工具名（探活成功时顺带取）。 */
    List<String> tools(String mcpCode);
}
