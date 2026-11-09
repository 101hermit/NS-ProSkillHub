package org.skillhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.skillhub.action.v1.AuthAction;
import org.skillhub.business.AuthService;
import org.skillhub.common.Actor;
import org.skillhub.common.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 身份解析过滤器：把 {@code Authorization: Bearer <token>} 换成 {@link Actor}。
 *
 * <p>为什么放在 {@code config} 而不是 {@code common}：它要依赖业务层的
 * {@link AuthService}，而 {@code common} 按分层纪律不得依赖任何其它包
 * （见 docs/01 的包依赖矩阵）。
 *
 * <p>判定顺序：
 * <ol>
 *   <li>有 Bearer 令牌且有效 → 用令牌里的身份（这是"登录"路径）</li>
 *   <li>令牌无效/过期 → 降级为请求头里的身份（默认访客），**不报 401** ——
 *       否则一个过期的令牌会让页面直接白屏，而正确做法是"以访客身份继续，提示重新登录"</li>
 *   <li>没有令牌 → 请求头 {@code X-SKH-Actor} / {@code X-SKH-Role}（演示与脚本路径）</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdentityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdentityFilter.class);

    private final AuthService authService;
    private final ActorContext actorContext;

    public IdentityFilter(AuthService authService, ActorContext actorContext) {
        this.authService = authService;
        this.actorContext = actorContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = AuthAction.bearer(request.getHeader("Authorization"));
        if (token != null && !token.isBlank()) {
            try {
                Actor actor = authService.resolve(token);
                if (actor != null) {
                    actorContext.bindActor(actor);
                    response.setHeader("X-SKH-Actor-Resolved", "token");
                } else {
                    response.setHeader("X-SKH-Actor-Resolved", "token-expired");
                }
            } catch (RuntimeException e) {
                // 数据库抖一下不应该让整个请求失败：降级为请求头身份
                log.warn("解析令牌失败，降级为请求头身份：{}", e.getMessage());
                response.setHeader("X-SKH-Actor-Resolved", "token-error");
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            actorContext.clear();
        }
    }
}
