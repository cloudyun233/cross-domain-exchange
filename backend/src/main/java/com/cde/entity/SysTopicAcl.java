package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 访问控制规则表。
 * <p>
 * 本表是业务数据库中的 ACL 规则源数据，应用通过 EMQX HTTP API同步到
 * EMQX 内置数据库授权（Built-in Database Authorization），用于 MQTT 主题级权限控制。
 * <p>
 * username 字段支持通配符 "*"，表示全局规则，对所有客户端生效；
 * 否则精确匹配客户端连接认证时提交的 username（当前系统中为登录用户名）。
 * <p>
 * 权限由 action 与 accessType 两个字段组合决定：
 * <ul>
 *   <li>action=publish + accessType=allow → 允许发布到匹配的主题</li>
 *   <li>action=subscribe + accessType=allow → 允许订阅匹配的主题</li>
 *   <li>action=all + accessType=allow → 同时允许发布和订阅</li>
 *   <li>accessType=deny → 拒绝对应的操作，deny 规则优先于 allow 规则</li>
 * </ul>
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
