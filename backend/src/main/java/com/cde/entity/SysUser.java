package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 客户端/用户表。
 * <p>
 * 具有双重身份：既是 Web 平台登录用户，也是 MQTT 客户端。
 * 用户登录后 JWT 同时包含 username 和 clientId：username 用于 Web API 认证，
 * 也作为 MQTT5 认证中的 EMQX username；clientId 作为 MQTT Client ID，
 * 用于连接标识和持久会话。
 * <p>
 * EMQX 内置数据库授权源按连接提交的 username 匹配 ACL 规则；
 * 当前使用登录用户名，与 sys_topic_acl.username 对应。
 * <p>
 * domain 字段标注 {@code @TableField(exist = false)}，不映射数据库列，
 * 仅在关联查询（如 JOIN sys_domain）时由 MyBatis-Plus 手动填充，
 * 用于避免额外的域信息查询。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属安全域ID */
    private Long domainId;

    /** 用户账号 */
    private String username;

    /** 加密后密码 */
    private String passwordHash;

    /** 角色类型 (producer/consumer/admin) */
    private String roleType;

    /** MQTT Client ID */
    private String clientId;

    /** 所属安全域(非数据库字段) */
    @TableField(exist = false)
    private SysDomain domain;
}
