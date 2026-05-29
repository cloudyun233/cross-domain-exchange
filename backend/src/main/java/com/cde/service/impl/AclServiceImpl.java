package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.entity.SysTopicAcl;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysTopicAclMapper;
import com.cde.mqtt.EmqxApiClient;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ACL服务实现
 *
 * <p>采用"CRUD→全量同步"模式，数据库始终作为 ACL 权限源。
 * 写操作失败时会回滚数据库事务，并尽力将 EMQX 恢复为写操作前的规则快照。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AclServiceImpl implements AclService {

    private final SysTopicAclMapper aclMapper;
    private final EmqxApiClient emqxApiClient;
    private final ReentrantLock aclSyncLock = new ReentrantLock();

    @Override
    public List<SysTopicAcl> listAll() { return aclMapper.selectList(null); }

    @Override
    public List<SysTopicAcl> listByUsername(String username) {
        return aclMapper.selectList(new LambdaQueryWrapper<SysTopicAcl>()
                .eq(SysTopicAcl::getUsername, username));
    }

    @Override
    @Transactional
    public SysTopicAcl create(SysTopicAcl acl) {
        boolean unlockOnExit = lockAclSyncUntilTransactionComplete();
        try {
            List<SysTopicAcl> beforeRules = aclMapper.selectList(null);
            aclMapper.insert(acl);
            syncCurrentRulesWithRestore(beforeRules);
            log.info("ACL规则创建并同步: username={}, topic={}, action={}, access={}",
                    acl.getUsername(), acl.getTopicFilter(), acl.getAction(), acl.getAccessType());
            return acl;
        } finally {
            if (unlockOnExit) {
                aclSyncLock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public SysTopicAcl update(Long id, SysTopicAcl acl) {
        boolean unlockOnExit = lockAclSyncUntilTransactionComplete();
        try {
            List<SysTopicAcl> beforeRules = aclMapper.selectList(null);
            acl.setId(id);
            int updated = aclMapper.updateById(acl);
            if (updated == 0) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "ACL规则不存在");
            }
            syncCurrentRulesWithRestore(beforeRules);
            return aclMapper.selectById(id);
        } finally {
            if (unlockOnExit) {
                aclSyncLock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        boolean unlockOnExit = lockAclSyncUntilTransactionComplete();
        try {
            List<SysTopicAcl> beforeRules = aclMapper.selectList(null);
            int deleted = aclMapper.deleteById(id);
            if (deleted == 0) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "ACL规则不存在");
            }
            syncCurrentRulesWithRestore(beforeRules);
        } finally {
            if (unlockOnExit) {
                aclSyncLock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public void deleteByUsername(String username) {
        boolean unlockOnExit = lockAclSyncUntilTransactionComplete();
        try {
            List<SysTopicAcl> beforeRules = aclMapper.selectList(null);
            aclMapper.delete(new LambdaQueryWrapper<SysTopicAcl>()
                    .eq(SysTopicAcl::getUsername, username));
            syncCurrentRulesWithRestore(beforeRules);
        } finally {
            if (unlockOnExit) {
                aclSyncLock.unlock();
            }
        }
    }

    @Override
    public void syncToEmqx() {
        aclSyncLock.lock();
        try {
            syncRulesOrThrow(aclMapper.selectList(null));
        } finally {
            aclSyncLock.unlock();
        }
    }

    private void syncCurrentRulesWithRestore(List<SysTopicAcl> beforeRules) {
        List<SysTopicAcl> currentRules = aclMapper.selectList(null);
        boolean synced = emqxApiClient.syncAllAclRules(currentRules);
        if (!synced) {
            restoreEmqxRules(beforeRules);
            throw syncFailure();
        }
        log.info("ACL规则全量同步到EMQX, 共{}条", currentRules.size());
    }

    private void syncRulesOrThrow(List<SysTopicAcl> allRules) {
        boolean synced = emqxApiClient.syncAllAclRules(allRules);
        if (!synced) {
            throw syncFailure();
        }
        log.info("ACL规则全量同步到EMQX, 共{}条", allRules.size());
    }

    private boolean lockAclSyncUntilTransactionComplete() {
        aclSyncLock.lock();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return true;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                aclSyncLock.unlock();
            }
        });
        return false;
    }

    private void restoreEmqxRules(List<SysTopicAcl> beforeRules) {
        boolean restored = emqxApiClient.syncAllAclRules(beforeRules);
        if (restored) {
            log.warn("ACL规则同步失败，已将EMQX恢复为变更前快照, rules={}", beforeRules.size());
        } else {
            log.error("ACL规则同步失败，且EMQX恢复变更前快照失败, rules={}", beforeRules.size());
        }
    }

    private BusinessException syncFailure() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "ACL规则同步到EMQX失败，请检查Broker状态后重试");
    }
}
