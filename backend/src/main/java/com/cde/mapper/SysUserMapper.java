package com.cde.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cde.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, d.domain_code, d.domain_name FROM sys_user u " +
            "LEFT JOIN sys_domain d ON u.domain_id = d.id " +
            "WHERE u.client_id = #{clientId}")
    SysUser selectByClientIdWithDomain(String clientId);
}
