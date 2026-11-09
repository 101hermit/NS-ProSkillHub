package org.skillhub.common;

/**
 * 业务异常：携带 {@link ErrorCode} 与已渲染的中文文案。
 *
 * <p>约定：业务层只抛这个异常，不抛 {@code RuntimeException("...")}。
 * 动作层不 catch，由 {@link GlobalExceptionHandler} 统一翻译成 HTTP 响应。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    private ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** 用错误码的模板 + 参数渲染文案。 */
    public static ApiException of(ErrorCode code, Object... args) {
        return new ApiException(code, code.format(args), null);
    }

    /** 使用完全自定义的文案（仍需给出错误码，便于机器判断）。 */
    public static ApiException ofMessage(ErrorCode code, String message) {
        return new ApiException(code, message, null);
    }

    public static ApiException of(ErrorCode code, Throwable cause, Object... args) {
        return new ApiException(code, code.format(args), cause);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
