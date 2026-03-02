package com.crossdomain.exchange.config;

import com.crossdomain.exchange.entity.Topic;
import com.crossdomain.exchange.entity.User;
import com.crossdomain.exchange.repository.TopicRepository;
import com.crossdomain.exchange.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据初始化类
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            initUsers();
        }
        if (topicRepository.count() == 0) {
            initTopics();
        }
    }

    private void initUsers() {
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@example.com")
                .role("ADMIN")
                .currentDomain("domain-1")
                .enabled(true)
                .build();

        User user1 = User.builder()
                .username("user1")
                .password(passwordEncoder.encode("user123"))
                .email("user1@example.com")
                .role("USER")
                .currentDomain("domain-1")
                .enabled(true)
                .build();

        User user2 = User.builder()
                .username("user2")
                .password(passwordEncoder.encode("user123"))
                .email("user2@example.com")
                .role("USER")
                .currentDomain("domain-2")
                .enabled(true)
                .build();

        User user3 = User.builder()
                .username("user3")
                .password(passwordEncoder.encode("user123"))
                .email("user3@example.com")
                .role("USER")
                .currentDomain("domain-3")
                .enabled(true)
                .build();

        User user4 = User.builder()
                .username("user4")
                .password(passwordEncoder.encode("user123"))
                .email("user4@example.com")
                .role("USER")
                .currentDomain("domain-4")
                .enabled(true)
                .build();

        userRepository.saveAll(List.of(admin, user1, user2, user3, user4));
    }

    private void initTopics() {
        Topic topic1 = Topic.builder()
                .topicName("domain-1/sensor/temperature")
                .sourceDomain("domain-1")
                .description("域1温度传感器数据")
                .isCrossDomain(false)
                .qos(1)
                .retained(false)
                .build();

        Topic topic2 = Topic.builder()
                .topicName("cross-domain/alerts")
                .sourceDomain("domain-1")
                .description("跨域告警信息")
                .isCrossDomain(true)
                .qos(2)
                .retained(true)
                .build();

        Topic topic3 = Topic.builder()
                .topicName("domain-2/device/status")
                .sourceDomain("domain-2")
                .description("域2设备状态")
                .isCrossDomain(false)
                .qos(1)
                .retained(false)
                .build();

        Topic topic4 = Topic.builder()
                .topicName("cross-domain/data-exchange")
                .sourceDomain("domain-2")
                .description("跨域数据交换")
                .isCrossDomain(true)
                .qos(2)
                .retained(false)
                .build();

        Topic topic5 = Topic.builder()
                .topicName("domain-3/sensor/humidity")
                .sourceDomain("domain-3")
                .description("域3湿度传感器数据")
                .isCrossDomain(false)
                .qos(0)
                .retained(false)
                .build();

        Topic topic6 = Topic.builder()
                .topicName("domain-4/system/logs")
                .sourceDomain("domain-4")
                .description("域4系统日志")
                .isCrossDomain(false)
                .qos(1)
                .retained(false)
                .build();

        topicRepository.saveAll(List.of(topic1, topic2, topic3, topic4, topic5, topic6));
    }
}
