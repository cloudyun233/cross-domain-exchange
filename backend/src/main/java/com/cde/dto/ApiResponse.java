package com.cde.dto;

import lombok.Data;

import java.util.Map;

/**
 * 通用API响应包装类，支持成功/失败/否定确认三种响应变体。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>ok() — 成功响应，携带数据</li>
 *   <li>fail() — 一般失败响应，仅含错误信息</li>
 *   <li>nack() — 否定确认响应，携带错误码和可选详情，用于需要结构化错误信息的场景</li>
 * </ul>
 *
 * @param <T> 响应体数据类型
 */
@Data
public class ApiResponse<T> {
    /** 是否成功 */
    private boolean success;
    /** 响应消息 */
    private String message;
    /** 错误码（nack场景使用） */
    private String code;
    /** 错误详情键值对（nack场景使用） */
    private Map<String, Object> details;
    /** 响应数据 */
    private T data;

    /**
     * 构建成功响应，使用默认消息
     *
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(true);
        r.setMessage("操作成功");
        r.setData(data);
        return r;
    }

    /**
     * 构建成功响应，使用自定义消息
     *
     * @param message 自定义成功消息
     * @param data    响应数据
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(true);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    /**
     * 构建一般失败响应
     *
     * @param message 失败消息
     * @return 失败响应
     */
    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }

    /**
     * 构建否定确认响应，携带错误码
     *
     * @param code    错误码，用于客户端精确识别错误类型
     * @param message 错误描述
     * @return 否定确认响应
     */
    public static <T> ApiResponse<T> nack(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    /**
     * 构建否定确认响应，携带错误码和详细错误信息
     *
     * @param code    错误码
     * @param message 错误描述
     * @param details 附加错误详情键值对
     * @return 否定确认响应
     */
    public static <T> ApiResponse<T> nack(String code, String message, Map<String, Object> details) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setSuccess(false);
        r.setCode(code);
        r.setMessage(message);
        r.setDetails(details);
        return r;
    }
}
