package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.service.TopicService;
import com.cde.service.converter.DataConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;
    private final List<DataConverter> dataConverters;
    private final ObjectMapper objectMapper;

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> getTopicTree() {
        return ApiResponse.ok(topicService.buildDomainTopicTree());
    }

    /**
     * 发布消息 (论文4.4.2: 含数据格式转换拦截)
     * ACL校验由EMQX全权负责
     */
    @PostMapping("/publish")
    public ApiResponse<Void> publish(
            @RequestParam String topic,
            @RequestBody String payload,
            @RequestParam(defaultValue = "1") int qos,
            @RequestParam(defaultValue = "json") String format,
            @RequestHeader("Authorization") String authHeader,
            Authentication auth) {

        String username = auth.getName();
        String token = authHeader.replace("Bearer ", "");

        // 数据格式转换 (论文4.4.2: 拦截器机制)
        String convertedPayload = payload;
        String sourceFormat = format;
        if (!"json".equalsIgnoreCase(format)) {
            for (DataConverter converter : dataConverters) {
                if (converter.supports(format)) {
                    convertedPayload = converter.convertToJson(payload);
                    sourceFormat = format;
                    log.info("数据格式转换: {} → JSON, converter={}", format, converter.getClass().getSimpleName());
                    break;
                }
            }
        }

        // 如果发生了转换，在payload中附带来源标记
        if (!format.equalsIgnoreCase("json")) {
            try {
                Map<String, Object> meta = Map.of(
                        "source_format", sourceFormat,
                        "converter", sourceFormat.toUpperCase() + "-Converter"
                );
                Map<String, Object> wrapper = Map.of(
                        "_meta", meta,
                        "data", objectMapper.readValue(convertedPayload, Object.class)
                );
                convertedPayload = objectMapper.writeValueAsString(wrapper);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to wrap converted payload", e);
            }
        }

        // ACL校验由EMQX全权负责，使用用户级连接发布
        topicService.publishMsg(topic, convertedPayload, qos, username, token);
        return ApiResponse.ok("消息发布成功", null);
    }
}
