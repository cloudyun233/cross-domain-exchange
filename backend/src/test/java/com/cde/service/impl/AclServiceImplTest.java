package com.cde.service.impl;

import com.cde.entity.SysTopicAcl;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysTopicAclMapper;
import com.cde.mqtt.EmqxApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclServiceImplTest {

    @Mock
    private SysTopicAclMapper aclMapper;

    @Mock
    private EmqxApiClient emqxApiClient;

    @InjectMocks
    private AclServiceImpl aclService;

    @Test
    void createUsesFullSyncInsteadOfIncrementalPush() {
        SysTopicAcl acl = acl("producer_medical_swh", "cross_domain/medical/swh");
        List<SysTopicAcl> beforeRules = List.of();
        List<SysTopicAcl> afterRules = List.of(acl);
        when(aclMapper.selectList(null)).thenReturn(beforeRules, afterRules);
        when(emqxApiClient.syncAllAclRules(afterRules)).thenReturn(true);

        aclService.create(acl);

        verify(emqxApiClient).syncAllAclRules(afterRules);
        verify(emqxApiClient, never()).pushAclRule(acl);
    }

    @Test
    void updateRestoresPreviousEmqxRulesWhenFullSyncFails() {
        SysTopicAcl oldAcl = acl("producer_medical_swh", "cross_domain/medical/swh");
        SysTopicAcl newAcl = acl("producer_finance_swh", "cross_domain/finance/swh");
        List<SysTopicAcl> beforeRules = List.of(oldAcl);
        List<SysTopicAcl> afterRules = List.of(newAcl);
        when(aclMapper.selectList(null)).thenReturn(beforeRules, afterRules);
        when(emqxApiClient.syncAllAclRules(afterRules)).thenReturn(false);
        when(emqxApiClient.syncAllAclRules(beforeRules)).thenReturn(true);

        assertThatThrownBy(() -> aclService.update(1L, newAcl))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL规则同步到EMQX失败，请检查Broker状态后重试");

        var inOrder = inOrder(aclMapper, emqxApiClient);
        inOrder.verify(aclMapper).selectList(null);
        inOrder.verify(aclMapper).updateById(newAcl);
        inOrder.verify(aclMapper).selectList(null);
        inOrder.verify(emqxApiClient).syncAllAclRules(afterRules);
        inOrder.verify(emqxApiClient).syncAllAclRules(beforeRules);
    }

    @Test
    void syncToEmqxThrowsBusinessExceptionWhenFullSyncFails() {
        SysTopicAcl acl = acl("producer_medical_swh", "cross_domain/medical/swh");
        List<SysTopicAcl> rules = List.of(acl);
        when(aclMapper.selectList(null)).thenReturn(rules);
        when(emqxApiClient.syncAllAclRules(rules)).thenReturn(false);

        assertThatThrownBy(() -> aclService.syncToEmqx())
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL规则同步到EMQX失败，请检查Broker状态后重试")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void transactionalWritesKeepSyncLockUntilTransactionCompletes() {
        SysTopicAcl acl = acl("producer_medical_swh", "cross_domain/medical/swh");
        List<SysTopicAcl> beforeRules = List.of();
        List<SysTopicAcl> afterRules = List.of(acl);
        when(aclMapper.selectList(null)).thenReturn(beforeRules, afterRules);
        when(emqxApiClient.syncAllAclRules(afterRules)).thenReturn(true);
        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(aclService, "aclSyncLock");

        TransactionSynchronizationManager.initSynchronization();
        try {
            aclService.create(acl);

            assertThat(lock).isNotNull();
            assertThat(lock.isHeldByCurrentThread()).isTrue();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertThat(lock.isHeldByCurrentThread()).isFalse();
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            while (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Test
    void writeOperationsAreTransactional() throws NoSuchMethodException {
        Method create = AclServiceImpl.class.getMethod("create", SysTopicAcl.class);
        Method update = AclServiceImpl.class.getMethod("update", Long.class, SysTopicAcl.class);
        Method delete = AclServiceImpl.class.getMethod("delete", Long.class);

        assertThat(create.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(update.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(delete.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private SysTopicAcl acl(String username, String topicFilter) {
        SysTopicAcl acl = new SysTopicAcl();
        acl.setUsername(username);
        acl.setTopicFilter(topicFilter);
        acl.setAction("publish");
        acl.setAccessType("allow");
        return acl;
    }
}
