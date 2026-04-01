package com.cde.dto;

import lombok.Data;

/**
 * EMQX ACL校验请求DTO (论文4.2.3)
 */
@Data
public class AclReqDTO {
    private String access;    // publish / subscribe
    private String username;  // client_id
    private String topic;     // 目标主题
}
