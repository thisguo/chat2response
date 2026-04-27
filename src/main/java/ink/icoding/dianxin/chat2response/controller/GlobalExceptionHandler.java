package ink.icoding.dianxin.chat2response.controller;

import ink.icoding.dianxin.chat2response.auth.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器, 统一输出JSON错误结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = "参数校验失败";
        if (!ex.getBindingResult().getFieldErrors().isEmpty()) {
            FieldError fieldError = ex.getBindingResult().getFieldErrors().get(0);
            message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(errorBody("invalid_request_error", message));
    }

    /**
     * 处理非法参数异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(errorBody("invalid_request_error", ex.getMessage()));
    }

    /**
     * 处理状态异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("conflict_error", ex.getMessage()));
    }

    /**
     * 处理未授权异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("unauthorized_error", ex.getMessage()));
    }

    /**
     * 处理其他未捕获异常。
     *
     * @param ex      异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("internal_error", "服务异常: " + request.getRequestURI()));
    }

    private Map<String, Object> errorBody(String type, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", type);
        error.put("message", message);

        Map<String, Object> result = new HashMap<>();
        result.put("error", error);
        result.put("timestamp", Instant.now().getEpochSecond());
        return result;
    }
}
