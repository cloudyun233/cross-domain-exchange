package com.crossdomain.exchange.repository;

import com.crossdomain.exchange.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 主题Repository接口
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByTopicName(String topicName);

    List<Topic> findBySourceDomain(String sourceDomain);

    List<Topic> findByIsCrossDomain(Boolean isCrossDomain);

    boolean existsByTopicName(String topicName);
}
