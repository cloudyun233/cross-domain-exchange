package com.cde.service;

import com.cde.entity.SysTopicAcl;
import java.util.List;

public interface AclService {
    List<SysTopicAcl> listAll();
    List<SysTopicAcl> listByUsername(String username);
    SysTopicAcl create(SysTopicAcl acl);
    SysTopicAcl update(Long id, SysTopicAcl acl);
    void delete(Long id);
    /** 全量同步ACL到EMQX Broker */
    void syncToEmqx();
}
