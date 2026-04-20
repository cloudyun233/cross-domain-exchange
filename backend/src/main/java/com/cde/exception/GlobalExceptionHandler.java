package com.cde.exception;

import com.cde.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.nack("AUTH_FAILED", e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        Map<String, Object> details = Map.of(
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.nack(resolveCode(e.getStatus()), e.getMessage(), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Request processing failed", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.nack("INTERNAL_ERROR", "服务处理失败: " + e.getMessage()));
    }

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
