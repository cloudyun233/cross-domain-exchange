package com.cde.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 安全域表
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
