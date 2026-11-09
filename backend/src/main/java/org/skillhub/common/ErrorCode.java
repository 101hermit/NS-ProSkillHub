package org.skillhub.common;

/**
 * 全局错误码。
 *
 * <p>编码规则（三系统共用同一张表，见 docs/03 §5）：
 * <ul>
 *   <li>0        成功</li>
 *   <li>1xxx     参数 / 未找到 / 冲突 / 状态 / 权限</li>
 *   <li>2xxx     Skill-MS 库语义拦截（仅标准版/专业版产生）</li>
 *   <li>3xxx     外部服务（Agent / MCP / 出网）</li>
 *   <li>4xxx     能力未启用</li>
 *   <li>5xxx     内部错误</li>
 *   <li>6xxx     离线降级（业务成功，但结果是本地兜底产生的）</li>
 * </ul>
 *
 * <p>每个错误码都带 {@code httpStatus} 与中文模板；{@code msg} 必须能直接展示给最终用户。
 */
public enum ErrorCode {

    OK(0, 200, "ok"),

    INVALID_PARAM(1000, 400, "参数不合法：%s。怎么办：检查后重试。"),
    MISSING_PARAM(1001, 400, "缺少必填参数：%s。"),
    NOT_FOUND(1100, 404, "找不到%s。怎么办：确认标识是否正确，或先在列表页搜索。"),
    SKILL_NOT_FOUND(1101, 404, "找不到这个 Skill（%s）。怎么办：刷新列表，它可能已被删除。"),
    CONFLICT(1200, 409, "操作冲突：%s。"),
    VERSION_EXISTS(1201, 409, "版本 %s 已存在。怎么办：换一个版本号，或用「编辑」追加内容。"),
    STATE_ILLEGAL(1300, 409, "当前状态 %s 不允许 %s。怎么办：先完成上一步。"),
    ROLE_FORBIDDEN(1400, 403, "当前身份（%s）无此权限。怎么办：以管理员身份访问。"),

    MS_CONSTRAINT_VIOLATION(2000, 422, "被约束 %s 拦截：%s。怎么办：%s"),
    MS_EVENT_DEAD(2003, 409, "事件 %s 已进入死信队列：%s。怎么办：到「事件」页手动重试。"),
    QUERY_REJECTED(2100, 409, "查询语句第 %s 个词「%s」不认识。怎么办：可用列：%s"),

    UPSTREAM_FAILED(3000, 502, "外部服务调用失败：%s。怎么办：%s"),
    AGENT_UNAVAILABLE(3001, 502, "智能体「%s」不可达：%s。怎么办：%s"),
    MCP_UNAVAILABLE(3002, 502, "MCP 服务「%s」不可达：%s。怎么办：确认服务已启动且 endpoint 正确。"),
    EGRESS_BLOCKED(3003, 502, "出网被阻断（%s）。怎么办：%s"),
    UPSTREAM_TIMEOUT(3004, 504, "外部服务超时（%s ms）。怎么办：调小 max_tokens，或换更快的模型。"),

    NOT_ENABLED(4000, 501, "该能力在本版本未启用（%s）。怎么办：%s"),

    INTERNAL_ERROR(5000, 500, "服务内部错误（追踪号 %s）。怎么办：把追踪号提供给管理员，或在日志里 grep 它。"),
    DB_ERROR(5001, 500, "数据库操作失败：%s。怎么办：见 docs/05 §5 的数据库排障。"),

    OFFLINE_MODE(6000, 200, "当前离线（%s），返回本地生成的结果。");

    private final int code;
    private final int httpStatus;
    private final String template;

    ErrorCode(int code, int httpStatus, String template) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.template = template;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String template() {
        return template;
    }

    /** 用模板渲染人话文案；参数个数不匹配时退化为模板原文，绝不抛异常。 */
    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (RuntimeException ignore) {
            return template;
        }
    }
}
