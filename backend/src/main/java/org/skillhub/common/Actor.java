package org.skillhub.common;

/**
 * 轻量身份上下文。
 *
 * <p><b>这不是鉴权体系</b>：本版三系统都不做登录。它只承载"谁在做这件事"，
 * 用于审计（{@code op_log.actor}）、事件派发与权限提示。
 * 生产接入 SSO 时，只需把 {@link ActorContext} 的实现从"读请求头"换成"解析令牌"，
 * 业务层与动作层代码零改动。
 *
 * @param name 操作者标识，默认 {@code guest}
 * @param role {@code viewer} / {@code editor} / {@code admin}，默认 {@code viewer}
 */
public record Actor(String name, String role) {

    public static final Actor GUEST = new Actor("guest", "viewer");

    public boolean canEdit() {
        return "editor".equals(role) || "admin".equals(role);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
