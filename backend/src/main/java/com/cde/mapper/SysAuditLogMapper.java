package com.cde.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cde.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_audit_log表MyBatis-Plus映射器。
 * <p>
 * 依赖BaseMapper提供基础CRUD，业务层通过LambdaQueryWrapper构建条件查询。
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
