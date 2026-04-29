package com.cde.service;

import com.cde.entity.SysTopicAcl;
import java.util.List;

/**
 * ACL规则服务接口
 *
 * <p>提供ACL规则的CRUD操作，并在变更后实时同步到EMQX Broker。
 * 核心方法 {@link #syncToEmqx()} 执行全量替换——将数据库中所有ACL规则
 * 一次性推送到Broker，覆盖其内置ACL列表，确保两端数据一致。</p>
 */
public interface AclService {
    List<SysTopicAcl> listAll();
    List<SysTopicAcl> listByUsername(String username);
    SysTopicAcl create(SysTopicAcl acl);
    SysTopicAcl update(Long id, SysTopicAcl acl);
    void delete(Long id);
    /** 全量同步ACL到EMQX Broker */
    void syncToEmqx();
}
