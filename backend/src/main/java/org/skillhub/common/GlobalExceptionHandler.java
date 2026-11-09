package org.skillhub.common;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常翻译：把一切失败变成 {@code {code, msg, trace_id}}，并设置合适的 HTTP 状态码。
 *
 * <p>铁律：{@code msg} 必须是"人话原因 + 怎么办"，不得只输出堆栈或英文技术词。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> onApiException(ApiException ex) {
        ErrorCode code = ex.errorCode();
        // 5xxx 才需要堆栈；业务校验失败只记一行
        if (code.code() >= 5000) {
            log.error("业务异常 {}: {}", code, ex.getMessage(), ex);
        } else {
            log.info("业务拒绝 {}: {}", code, ex.getMessage());
        }
        return ResponseEntity.status(code.httpStatus()).body(ApiResult.fail(code, ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResult<Void>> onValidation(Exception ex) {
        String detail = extractFieldErrors(ex);
        ErrorCode code = ErrorCode.INVALID_PARAM;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code, code.format(detail)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> onMissingParam(MissingServletRequestParameterException ex) {
        ErrorCode code = ErrorCode.MISSING_PARAM;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code, code.format(ex.getParameterName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> onUnreadable(HttpMessageNotReadableException ex) {
        ErrorCode code = ErrorCode.INVALID_PARAM;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code, code.format("请求体不是合法 JSON（或字段类型不对）")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> onTooLarge(MaxUploadSizeExceededException ex) {
        ErrorCode code = ErrorCode.INVALID_PARAM;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code, "上传内容超过大小上限（默认 64 MB）。怎么办：只传 SKILL.md 与必要附件。"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> onMethod(HttpRequestMethodNotSupportedException ex) {
        ErrorCode code = ErrorCode.INVALID_PARAM;
        return ResponseEntity.status(405).body(ApiResult.fail(code,
                "接口不支持 " + ex.getMethod() + " 方法。怎么办：检查请求方法。"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResult<Void>> onNoHandler(NoHandlerFoundException ex) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code, code.format("接口 " + ex.getRequestURL())));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResult<Void>> onDb(DataAccessException ex) {
        log.error("数据库操作失败", ex);
        ErrorCode code = ErrorCode.DB_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResult.fail(code, code.format(rootCauseMessage(ex))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> onUnknown(Exception ex) {
        log.error("未捕获异常 trace={}", Trace.current(), ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(code, code.format(Trace.current())));
    }

    private static String extractFieldErrors(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv) {
            return manv.getBindingResult().getFieldErrors().stream()
                    .map(GlobalExceptionHandler::describe)
                    .collect(Collectors.joining("；"));
        }
        if (ex instanceof BindException be) {
            return be.getBindingResult().getFieldErrors().stream()
                    .map(GlobalExceptionHandler::describe)
                    .collect(Collectors.joining("；"));
        }
        return ex.getMessage() == null ? "未知字段问题" : ex.getMessage();
    }

    private static String describe(FieldError fe) {
        String msg = fe.getDefaultMessage();
        return fe.getField() + (msg == null ? " 不合法" : " " + msg);
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (msg == null) {
            return cur.getClass().getSimpleName();
        }
        // 只保留第一行，避免把多行 SQL 报错整段抛给用户
        String[] lines = msg.split("\\R");
        return Arrays.stream(lines).findFirst().orElse(msg).trim();
    }
}
