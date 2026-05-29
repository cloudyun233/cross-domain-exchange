package com.cde.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 简单的用户名/密码登录请求。
 */
@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}
