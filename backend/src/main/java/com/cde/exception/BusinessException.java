package com.cde.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常，携带 HTTP 状态码，用于表达领域级错误语义。
 *
 * <p>与 Spring 内置异常的区别：Spring 的异常体系面向框架层（如参数绑定、类型转换），
 * 而 BusinessException 面向业务层，由开发者主动抛出，用于表达 ACL 拒绝、格式校验失败、
 * 资源不存在等场景。构造时指定 HttpStatus，由 {@link GlobalExceptionHandler} 统一捕获
 * 并映射为标准 nack 响应返回给前端。</p>
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
