package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.service.AuthService;
import com.cde.service.TopicService;
import com.cde.service.converter.DataConverter;
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
    private final AuthService authService;
    private final List<DataConverter> dataConverters;

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> getTopicTree() {
        return ApiResponse.ok(topicService.buildDomainTopicTree());
    }

    /**
     * 发布消息 (论文4.4.2: 含数据格式转换拦截)
     */
    @PostMapping("/publish")
    public ApiResponse<Void> publish(
            @RequestParam String topic,
            @RequestBody String payload,
            @RequestParam(defaultValue = "1") int qos,
            @RequestParam(defaultValue = "json") String format,
            Authentication auth) {

        String clientId = auth.getName();

        // ACL权限校验 (论文4.2.4)
        if (!authService.checkACL(clientId, topic, "publish")) {
            return ApiResponse.fail("ACL校验失败：用户 " + clientId + " 无发布权限，主题=" + topic);
        }

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
            convertedPayload = "{\"_meta\":{\"source_format\":\"" + sourceFormat
                    + "\",\"converter\":\"" + sourceFormat.toUpperCase() + "-Converter\"},"
                    + "\"data\":" + convertedPayload + "}";
        }

        topicService.publishMsg(topic, convertedPayload, qos, clientId);
        return ApiResponse.ok("消息发布成功", null);
    }
}
