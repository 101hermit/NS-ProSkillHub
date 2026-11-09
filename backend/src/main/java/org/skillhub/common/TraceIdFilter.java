package org.skillhub.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求注入链路追踪号与身份上下文，并写进响应头。
 *
 * <p>顺序最早（{@link Ordered#HIGHEST_PRECEDENCE}），保证后续所有日志都带 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private final ActorContext actorContext;

    public TraceIdFilter(ActorContext actorContext) {
        this.actorContext = actorContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(Trace.HEADER);
        String traceId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                : incoming.trim();
        MDC.put(Trace.MDC_KEY, traceId);
        response.setHeader(Trace.HEADER, traceId);
        actorContext.bind(request); // 先用请求头给一个身份，令牌解析成功后由 IdentityFilter 覆盖
        try {
            chain.doFilter(request, response);
        } finally {
            actorContext.clear();
            MDC.remove(Trace.MDC_KEY);
        }
    }
}
