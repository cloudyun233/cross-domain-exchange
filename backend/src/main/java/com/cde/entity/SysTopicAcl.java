package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 访问控制规则表
 */
@Data
@TableName("sys_topic_acl")
public class SysTopicAcl {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** EMQX Username (用于ACL匹配,支持*通配) */
    private String username;

    /** 主题过滤器 */
    private String topicFilter;

    /** 动作 (publish/subscribe/all) */
    private String action;

    /** 访问类型 (allow/deny) */
    private String accessType;
}
