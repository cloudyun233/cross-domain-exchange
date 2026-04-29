package com.cde.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cde.entity.SysDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * sys_domain表MyBatis-Plus映射器。
 * <p>
 * countByParentId用于领域删除安全校验，防止删除仍有子领域的父领域。
 */
@Mapper
public interface SysDomainMapper extends BaseMapper<SysDomain> {

    /**
     * 统计指定父领域下的子领域数量，用于删除前的安全校验
     *
     * @param parentId 父领域ID
     * @return 子领域数量
     */
    @Select("SELECT COUNT(*) FROM sys_domain WHERE parent_id = #{parentId}")
    int countByParentId(Long parentId);
}
