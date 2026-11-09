package org.skillhub.common;

/**
 * 统一响应信封：{@code {code, msg, data, trace_id}}。
 *
 * <p>{@code code = 0} 表示成功；非 0 见 {@link ErrorCode}。
 * {@code msg} 永远是可直接展示的中文。
 */
public record ApiResult<T>(int code, String msg, T data, String traceId) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data, Trace.current());
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null, Trace.current());
    }

    public static <T> ApiResult<T> fail(ErrorCode code, String msg) {
        return new ApiResult<>(code.code(), msg, null, Trace.current());
    }

    /** 业务成功但走了离线兜底：code=6000，data 照常返回。 */
    public static <T> ApiResult<T> offline(T data, String reason) {
        return new ApiResult<>(ErrorCode.OFFLINE_MODE.code(), ErrorCode.OFFLINE_MODE.format(reason),
                data, Trace.current());
    }
}
