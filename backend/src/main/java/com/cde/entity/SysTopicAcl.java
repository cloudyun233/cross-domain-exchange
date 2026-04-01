package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 访问控制规则表 (论文表4-9)
 */
@Data
@TableName("sys_topic_acl")
public class SysTopicAcl {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端ID (支持*通配) */
    private String clientId;

    /** 主题过滤器 */
    private String topicFilter;

    /** 动作 (publish/subscribe/all) */
    private String action;

    /** 访问类型 (allow/deny) */
    private String accessType;

    /** 创建时间 */
    private LocalDateTime createTime;
}
