package com.cde.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cde.entity.SysDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysDomainMapper extends BaseMapper<SysDomain> {

    @Select("SELECT COUNT(*) FROM sys_domain WHERE parent_id = #{parentId}")
    int countByParentId(Long parentId);
}
