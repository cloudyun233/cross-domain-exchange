package com.cde.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 安全域表。
 * <p>
 * 采用自引用父子结构（parentId 指向自身表的主键）实现域的层级划分，
 * 顶级域的 parentId 为 NULL。子域继承父域的安全策略，形成树形隔离边界。
 * <p>
 * domainCode 与 MQTT 主题路径的映射关系：domainCode 作为主题路径段参与构建，
 * 例如 domainCode="factory1" 对应主题前缀 "factory1/"，从而实现域级别的主题隔离。
 * <p>
 * status 字段控制域的启用/禁用状态：1 表示启用，0 表示禁用。
 * 禁用域下的所有用户和子域应被拒绝访问，需在业务层级联判断。
 */
@Data
@TableName("sys_domain")
public class SysDomain {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父域ID，NULL表示顶级域 */
    private Long parentId;

    /** 域编码，建议使用英文/拼音 */
    private String domainCode;

    /** 域名称，可使用中文 */
    private String domainName;

    /** 状态(1:启用, 0:禁用) */
    private Integer status;
}
