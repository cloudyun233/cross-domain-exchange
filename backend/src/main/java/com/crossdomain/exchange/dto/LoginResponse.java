package com.crossdomain.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    @Default
    private String type = "Bearer";
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String currentDomain;
}
