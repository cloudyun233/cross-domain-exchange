package com.cde.dto;

import lombok.Data;

/**
 * EMQX ACL校验请求DTO (论文4.2.3)。
 * <p>
 * 字段与EMQX授权检查HTTP回调请求格式一一对应：
 * <ul>
 *   <li>access — 请求的操作类型：publish 或 subscribe</li>
 *   <li>username 字段：EMQX客户端的username，当前对应登录用户名，用于ACL匹配</li>
 *   <li>topic — 客户端请求发布/订阅的目标主题</li>
 * </ul>
 */
@Data
public class AclReqDTO {
    private String access;    // publish / subscribe
    private String username;  // EMQX user
    private String topic;     // 目标主题
}
