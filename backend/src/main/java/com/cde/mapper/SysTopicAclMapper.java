package com.cde.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cde.entity.SysTopicAcl;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_topic_acl表MyBatis-Plus映射器。
 * <p>
 * 依赖BaseMapper提供基础CRUD。全量同步场景通过selectList(null)读取所有ACL规则。
 */
@Mapper
public interface SysTopicAclMapper extends BaseMapper<SysTopicAcl> {
}
