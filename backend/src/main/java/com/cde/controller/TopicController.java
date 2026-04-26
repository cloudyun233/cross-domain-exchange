package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.dto.DomainTreeNode;
import com.cde.exception.BusinessException;
import com.cde.service.AuditService;
import com.cde.service.JsonSchemaValidationService;
import com.cde.service.TopicService;
import com.cde.service.converter.DataConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;
    private final List<DataConverter> dataConverters;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final JsonSchemaValidationService jsonSchemaValidationService;

    @GetMapping("/tree")
    public ApiResponse<List<DomainTreeNode>> getTopicTree() {
        return ApiResponse.ok(topicService.buildDomainTopicTree());
    }

    @PostMapping("/publish")
    public ApiResponse<Void> publish(
            @RequestParam String topic,
            @RequestBody String payload,
            @RequestParam(defaultValue = "1") int qos,
            @RequestParam(defaultValue = "false") boolean retain,
            @RequestParam(defaultValue = "structured") String format,
            @RequestHeader("Authorization") String authHeader,
            Authentication auth
    ) {
        String username = auth.getName();
        String token = authHeader.replace("Bearer ", "");
        String convertedPayload;
        try {
            String actualFormat = resolveActualFormat(format, payload);
            convertedPayload = convertPayload(payload, actualFormat);
            jsonSchemaValidationService.validate(topic, convertedPayload, actualFormat);
        } catch (BusinessException e) {
            recordFormatConvertFailure(username, topic, format, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            recordFormatConvertFailure(username, topic, format, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        topicService.publishMsg(topic, convertedPayload, qos, retain, username, token);
        return ApiResponse.ok("消息发布成功", null);
    }

    private void recordFormatConvertFailure(String username, String topic, String format, String reason) {
        auditService.log(username, "format_convert_fail",
                "数据格式转换失败: topic=" + topic + ", format=" + format + ", reason=" + reason,
                "0.0.0.0");
    }

    private String resolveActualFormat(String format, String payload) {
        String normalizedFormat = format == null ? "structured" : format.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedFormat) {
            case "structured" -> detectStructuredFormat(payload);
            case "json", "xml", "text" -> normalizedFormat;
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的数据格式: " + format);
        };
    }

    private String detectStructuredFormat(String payload) {
        String trimmed = payload == null ? "" : payload.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        return trimmed.startsWith("<") ? "xml" : "json";
    }

    private String convertPayload(String payload, String actualFormat) {
        if ("text".equals(actualFormat)) {
            return payload;
        }

        DataConverter converter = dataConverters.stream()
                .filter(item -> item.supports(actualFormat))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "未找到可用的数据格式转换器: " + actualFormat));

        String convertedPayload = converter.convertToJson(payload);
        if (!"xml".equals(actualFormat)) {
            return convertedPayload;
        }

        try {
            Map<String, Object> meta = Map.of(
                    "source_format", actualFormat,
                    "converter", actualFormat.toUpperCase(Locale.ROOT) + "-Converter"
            );
            Map<String, Object> wrapper = Map.of(
                    "_meta", meta,
                    "data", objectMapper.readValue(convertedPayload, Object.class)
            );
            log.info("数据格式转换: {} -> JSON", actualFormat);
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "XML 转换后的消息封装失败: " + e.getOriginalMessage());
        }
    }
}
