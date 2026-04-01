package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.entity.SysTopicAcl;
import com.cde.mapper.SysTopicAclMapper;
import com.cde.mqtt.EmqxApiClient;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ACL服务实现
 * CRUD操作后自动实时推送到EMQX (论文3.6.1: ACL热更新)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AclServiceImpl implements AclService {

    private final SysTopicAclMapper aclMapper;
    private final EmqxApiClient emqxApiClient;

    @Override
    public List<SysTopicAcl> listAll() { return aclMapper.selectList(null); }

    @Override
    public List<SysTopicAcl> listByClientId(String clientId) {
        return aclMapper.selectList(new LambdaQueryWrapper<SysTopicAcl>()
                .eq(SysTopicAcl::getClientId, clientId));
    }

    @Override
    public SysTopicAcl create(SysTopicAcl acl) {
        acl.setCreateTime(LocalDateTime.now());
        aclMapper.insert(acl);
        // 实时推送到EMQX
        emqxApiClient.pushAclRule(acl);
        log.info("ACL规则创建并推送: client={}, topic={}, action={}, access={}",
                acl.getClientId(), acl.getTopicFilter(), acl.getAction(), acl.getAccessType());
        return acl;
    }

    @Override
    public SysTopicAcl update(Long id, SysTopicAcl acl) {
        acl.setId(id);
        aclMapper.updateById(acl);
        // 全量同步确保一致性
        syncToEmqx();
        return aclMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        aclMapper.deleteById(id);
        // 全量同步
        syncToEmqx();
    }

    @Override
    public void syncToEmqx() {
        List<SysTopicAcl> allRules = aclMapper.selectList(null);
        emqxApiClient.syncAllAclRules(allRules);
        log.info("ACL规则全量同步到EMQX, 共{}条", allRules.size());
    }
}
