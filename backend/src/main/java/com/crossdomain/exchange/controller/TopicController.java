package com.crossdomain.exchange.controller;

import com.crossdomain.exchange.dto.ApiResponse;
import com.crossdomain.exchange.entity.Topic;
import com.crossdomain.exchange.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 主题控制器
 */
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopicController {

    private final TopicRepository topicRepository;

    @GetMapping
    public ApiResponse<List<Topic>> getAllTopics() {
        return ApiResponse.success(topicRepository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Topic> getTopicById(@PathVariable Long id) {
        return topicRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("主题不存在"));
    }

    @GetMapping("/domain/{domain}")
    public ApiResponse<List<Topic>> getTopicsByDomain(@PathVariable String domain) {
        return ApiResponse.success(topicRepository.findBySourceDomain(domain));
    }

    @GetMapping("/cross-domain")
    public ApiResponse<List<Topic>> getCrossDomainTopics() {
        return ApiResponse.success(topicRepository.findByIsCrossDomain(true));
    }

    @PostMapping
    public ApiResponse<Topic> createTopic(@RequestBody Topic topic) {
        if (topicRepository.existsByTopicName(topic.getTopicName())) {
            return ApiResponse.error("主题已存在");
        }
        return ApiResponse.success("主题创建成功", topicRepository.save(topic));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTopic(@PathVariable Long id) {
        topicRepository.deleteById(id);
        return ApiResponse.success("主题删除成功", null);
    }
}
