package org.skillhub.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 从请求头解析 {@link Actor}：{@code X-SKH-Actor} 与 {@code X-SKH-Role}。
 *
 * <p>缺省 → {@code guest / viewer}。非法 role 值收敛为 {@code viewer}（不报错，
 * 因为身份头是辅助信息，不该让页面打不开）。
 */
@Component
public class ActorContext {

    public static final String H_ACTOR = "X-SKH-Actor";
    public static final String H_ROLE = "X-SKH-Role";

    private static final ThreadLocal<Actor> HOLDER = new ThreadLocal<>();

    /** 绑定本次请求的身份（由 {@link TraceIdFilter} 调用）。 */
    public void bind(HttpServletRequest request) {
        HOLDER.set(read(request));
    }

    public void clear() {
        HOLDER.remove();
    }

    /** 当前请求的身份。 */
    public Actor current() {
        Actor a = HOLDER.get();
        return a == null ? Actor.GUEST : a;
    }

    /** 由上层（IdentityFilter）把令牌解析出的身份绑定进来，覆盖请求头里的身份。 */
    public void bindActor(Actor actor) {
        if (actor != null) {
            HOLDER.set(actor);
        }
    }
    public static Actor read(HttpServletRequest request) {
        String name = trim(request.getHeader(H_ACTOR));
        String role = normalizeRole(trim(request.getHeader(H_ROLE)));
        return new Actor(name.isEmpty() ? "guest" : name, role);
    }

    private static String normalizeRole(String raw) {
        if ("admin".equalsIgnoreCase(raw)) {
            return "admin";
        }
        if ("editor".equalsIgnoreCase(raw)) {
            return "editor";
        }
        return "viewer";
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
