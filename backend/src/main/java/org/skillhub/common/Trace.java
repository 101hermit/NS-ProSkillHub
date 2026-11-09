package org.skillhub.common;

import org.slf4j.MDC;

/**
 * 链路追踪号：一次请求一个，写在响应头 {@code X-Trace-Id}、响应体 {@code trace_id} 与日志 MDC。
 *
 * <p>用户报障时只需要提供这 8 个字符，就能 grep 出这次请求的全部日志。
 */
public final class Trace {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private Trace() {
    }

    /** 当前请求的追踪号；不在请求线程里时返回 {@code "-"}。 */
    public static String current() {
        String v = MDC.get(MDC_KEY);
        return v == null || v.isEmpty() ? "-" : v;
    }
}
