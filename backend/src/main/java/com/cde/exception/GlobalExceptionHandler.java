package com.cde.exception;

import com.cde.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * 全局异常处理器，通过 {@code @RestControllerAdvice} 集中拦截控制器层抛出的异常，
 * 统一转换为 {@link ApiResponse} 的 nack 格式响应。
 *
 * <p>异常类型到 HTTP 状态码与错误码的映射关系：
 * <ul>
 *   <li>{@link BadCredentialsException} → 401 / AUTH_FAILED</li>
 *   <li>{@link AccessDeniedException}   → 403 / ACCESS_DENIED</li>
 *   <li>{@link BusinessException}       → 由异常自身携带的 status 决定 / 由 {@link #resolveCode} 映射</li>
 *   <li>其他未捕获异常                   → 500 / INTERNAL_ERROR</li>
 * </ul>
 *
 * <p>nack 响应格式：{@code { success=false, code="ERROR_CODE", message="描述", details=... }}</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理 Spring Security 凭证校验失败（用户名或密码错误），返回 401。 */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.nack("AUTH_FAILED", e.getMessage()));
    }

    /** 处理 Spring Security 访问拒绝（已认证但权限不足），返回 403。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.nack("ACCESS_DENIED", "无权访问该资源"));
    }

    /**
     * 处理业务异常，HTTP 状态码取自异常本身，错误码由 {@link #resolveCode} 映射，
     * 响应体附带 timestamp 详情用于问题排查。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        Map<String, Object> details = Map.of(
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.nack(resolveCode(e.getStatus()), e.getMessage(), details));
    }

    /** 兜底处理：捕获所有未被上方处理器匹配的异常，记录错误日志后返回 500。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Request processing failed", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.nack("INTERNAL_ERROR", "服务处理失败: " + e.getMessage()));
    }

    /**
     * 将 HTTP 状态码映射为语义化错误码，供前端判断错误类型。
     * <ul>
     *   <li>400 → INVALID_REQUEST</li>
     *   <li>401 → UNAUTHORIZED</li>
     *   <li>403 → ACCESS_DENIED</li>
     *   <li>404 → RESOURCE_NOT_FOUND</li>
     *   <li>其余 → BUSINESS_ERROR</li>
     * </ul>
     */
    private String resolveCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "ACCESS_DENIED";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            default -> "BUSINESS_ERROR";
        };
    }
}
