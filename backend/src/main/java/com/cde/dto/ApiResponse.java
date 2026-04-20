package com.cde.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private String code;
    private Map<String, Object> details;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(true);
        r.setMessage("操作成功");
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(true);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }

    public static <T> ApiResponse<T> nack(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> ApiResponse<T> nack(String code, String message, Map<String, Object> details) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setCode(code);
        r.setMessage(message);
        r.setDetails(details);
        return r;
    }
}
