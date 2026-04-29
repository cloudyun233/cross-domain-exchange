package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.entity.SysTopicAcl;
import com.cde.mapper.SysTopicAclMapper;
import com.cde.mqtt.EmqxApiClient;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ACL服务实现
 *
 * <p>采用"CRUD→推送"模式：create时单条推送（pushAclRule），update/delete时触发全量同步（syncToEmqx）。
 * 全量同步而非增量同步，避免EMQX端规则与数据库出现不一致。
 * 注意：syncToEmqx非线程安全，若并发调用可能导致重复推送，当前由单线程Controller保证串行。</p>
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
    public List<SysTopicAcl> listByUsername(String username) {
        return aclMapper.selectList(new LambdaQueryWrapper<SysTopicAcl>()
                .eq(SysTopicAcl::getUsername, username));
    }

    @Override
    public SysTopicAcl create(SysTopicAcl acl) {
        aclMapper.insert(acl);
        // 实时推送到EMQX
        emqxApiClient.pushAclRule(acl);
        log.info("ACL规则创建并推送: username={}, topic={}, action={}, access={}",
                acl.getUsername(), acl.getTopicFilter(), acl.getAction(), acl.getAccessType());
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
