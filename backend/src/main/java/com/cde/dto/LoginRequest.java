package com.cde.dto;

import lombok.Data;

/**
 * 简单的用户名/密码登录请求。
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
