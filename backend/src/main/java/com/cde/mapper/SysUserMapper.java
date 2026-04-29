package com.cde.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cde.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * sys_user表MyBatis-Plus映射器。
 * <p>
 * 自定义方法说明：
 * <ul>
 *   <li>selectByClientIdWithDomain — 关联sys_domain表查询用户及其领域信息，用于登录时构建用户档案</li>
 *   <li>countByDomainId — 统计领域下用户数量，用于领域删除前的安全校验</li>
 * </ul>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据clientId查询用户，关联领域表获取domainCode和domainName
     *
     * @param clientId MQTT客户端标识
     * @return 包含领域信息的用户实体
     */
    @Select("SELECT u.*, d.domain_code, d.domain_name FROM sys_user u " +
            "LEFT JOIN sys_domain d ON u.domain_id = d.id " +
            "WHERE u.client_id = #{clientId}")
    SysUser selectByClientIdWithDomain(String clientId);

    /**
     * 统计指定领域下的用户数量，用于领域删除安全校验
     *
     * @param domainId 领域ID
     * @return 该领域下的用户数量
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE domain_id = #{domainId}")
    int countByDomainId(Long domainId);
}
