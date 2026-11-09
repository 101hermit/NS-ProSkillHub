/**
 * 服务接口层：对外部世界的一切依赖，都以接口形式暴露给业务层。
 *
 * <p><b>为什么这里没有 {@code SkillRepository}</b>：在 SSM 架构里，MyBatis 的
 * {@code @Mapper} 接口本身就是"持久化的抽象"—— 它没有实现类、没有 SQL 泄漏到业务层、
 * 换数据库只改 SQL 不改签名。再加一层 {@code RepositoryPort} 只会得到
 * "接口转调接口"的空壳。因此 {@code port} 只承载**真正会换实现**的依赖。
 *
 * <p>标准版比资源站多了两类外部依赖：
 * <ul>
 *   <li>{@link org.skillhub.port.AgentPort} —— 调用远端智能体（OpenAI 兼容接口）</li>
 *   <li>{@link org.skillhub.port.McpPort} —— 调用 MCP 服务（HTTP 工具调用）</li>
 * </ul>
 *
 * <p><b>每个外部端口都必须有一个可离线运行的实现</b>：
 * {@code OfflineAgentAdapter} 与 {@code BuiltinMcpAdapter}。这样断网时
 * 实测链路、评分口径、页面交互全都还能跑（结果里显式标注 {@code offline=true}），
 * 功能链路与外部依赖彻底解耦 —— 这也是本系统能被自动验收的前提。
 */
package org.skillhub.port;
