package com.crossdomain.exchange.repository;

import com.crossdomain.exchange.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订阅Repository接口
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserId(Long userId);

    List<Subscription> findByTopicId(Long topicId);

    List<Subscription> findByDomainName(String domainName);

    List<Subscription> findByUserIdAndActive(Long userId, Boolean active);
}
