package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 安全域表
 */
@Data
@TableName("sys_domain")
public class SysDomain {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 域编码 (如 gov, medical) */
    private String domainCode;

    /** 域名称 */
    private String domainName;

    /** 状态 (1:启用, 0:禁用) */
    private Integer status;
}
