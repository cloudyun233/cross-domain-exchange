package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 客户端/用户表 (论文表4-8)
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属安全域ID */
    private Long domainId;

    /** MQTT接入ClientID */
    private String clientId;

    /** 加密后密码 */
    private String passwordHash;

    /** 角色类型 (producer/consumer/admin) */
    private String roleType;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 所属安全域(非数据库字段) */
    @TableField(exist = false)
    private SysDomain domain;
}
