package com.cde.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT认证登录响应，包含令牌和用户档案信息。
 * <p>
 * domainCode由领域层级路径段拼接而成，用于标识用户所属领域在层级中的位置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    /** JWT访问令牌 */
    private String token;
    /** 令牌过期时间戳（毫秒） */
    private long expires;
    /** 用户名 */
    private String username;
    /** 角色类型编码 */
    private String roleType;
    /** 角色显示名称 */
    private String roleName;
    /** 领域编码，由层级路径段拼接 */
    private String domainCode;
    /** 领域名称 */
    private String domainName;
    /** MQTT客户端标识 */
    private String clientId;
}
