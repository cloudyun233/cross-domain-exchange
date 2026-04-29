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

/**
 * 主题与消息发布 REST API
 *
 * <p>提供主题树查询和消息发布功能。消息发布遵循完整的格式转换流水线：
 * <ol>
 *   <li>格式解析（resolveActualFormat）：将 "structured" 自动检测为 json/xml，或直接使用指定格式</li>
 *   <li>格式转换（convertPayload）：通过 DataConverter 将 XML/JSON 转换为标准 JSON，XML 额外封装 _meta 元信息</li>
 *   <li>Schema 校验（jsonSchemaValidationService）：对转换后的 JSON 进行 Schema 校验，校验失败仅记录审计日志不阻断发布</li>
 *   <li>MQTT 发布（topicService.publishMsg）：将转换后的消息通过 MQTT 发送至 Broker</li>
 *   <li>审计记录：格式转换失败和 Schema 校验失败均记录审计日志</li>
 * </ol>
 *
 * <p>API 列表：
 * <ul>
 *   <li>GET  /api/topics/tree   — 查询域-主题树形结构</li>
 *   <li>POST /api/topics/publish — 发布消息（含格式转换流水线）</li>
 * </ul>
 */
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

    /**
     * 查询域-主题树形结构
     *
     * <p>将安全域与主题组装为树形结构，供前端树形控件展示。
     *
     * @return 域-主题树形节点列表
     */
    @GetMapping("/tree")
    public ApiResponse<List<DomainTreeNode>> getTopicTree() {
        return ApiResponse.ok(topicService.buildDomainTopicTree());
    }

    /**
     * 发布消息
     *
     * <p>执行完整的消息发布流水线：
     * <ol>
     *   <li>格式解析：根据 format 参数确定实际数据格式（structured 自动检测）</li>
     *   <li>格式转换：通过 DataConverter 将原始格式转换为标准 JSON</li>
     *   <li>Schema 校验：对转换后的 JSON 进行 Schema 校验，失败仅记录审计不阻断</li>
     *   <li>MQTT 发布：将转换后的消息通过 MQTT 发送至 Broker</li>
     * </ol>
     * 格式转换失败时抛出异常并记录审计日志。
     *
     * @param topic      目标 MQTT 主题
     * @param payload    消息内容（原始格式）
     * @param qos        服务质量等级，默认 1
     * @param retain     是否保留消息，默认 false
     * @param format     数据格式（structured/json/xml/text），默认 structured
     * @param authHeader Authorization 请求头，JWT 令牌用于 MQTT 认证
     * @param auth       Spring Security 认证信息
     */
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
        String actualFormat;
        try {
            actualFormat = resolveActualFormat(format, payload);
            convertedPayload = convertPayload(payload, actualFormat);
        } catch (BusinessException e) {
            recordFormatConvertFailure(username, topic, format, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            recordFormatConvertFailure(username, topic, format, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        recordJsonSchemaValidationFailureIfAny(username, topic, actualFormat, convertedPayload);
        topicService.publishMsg(topic, convertedPayload, qos, retain, username, token);
        return ApiResponse.ok("消息发布成功", null);
    }

    private void recordFormatConvertFailure(String username, String topic, String format, String reason) {
        auditService.log(username, "format_convert_fail",
                "数据格式转换失败: topic=" + topic + ", format=" + format + ", reason=" + reason,
                "0.0.0.0");
    }

    private void recordJsonSchemaValidationFailureIfAny(String username, String topic, String actualFormat, String payload) {
        try {
            jsonSchemaValidationService.validate(topic, payload, actualFormat);
        } catch (RuntimeException e) {
            auditService.log(username, "json_schema_validate_fail",
                    "JSON Schema 校验失败但继续发布: topic=" + topic + ", format=" + actualFormat + ", reason=" + e.getMessage(),
                    "0.0.0.0");
        }
    }

    /**
     * 解析实际数据格式
     *
     * <p>将前端传入的 format 参数标准化：若为 "structured" 则根据消息内容自动检测（json/xml），
     * 若为 "json"/"xml"/"text" 则直接使用，其他值抛出不支持异常。
     *
     * @param format  前端指定的格式标识
     * @param payload 消息内容，用于自动检测
     * @return 标准化后的格式名称（json/xml/text）
     */
    private String resolveActualFormat(String format, String payload) {
        String normalizedFormat = format == null ? "structured" : format.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedFormat) {
            case "structured" -> detectStructuredFormat(payload); // 自动检测：根据内容首字符判断
            case "json", "xml", "text" -> normalizedFormat;       // 明确指定：直接使用
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的数据格式: " + format);
        };
    }

    /**
     * 自动检测结构化数据的实际格式
     *
     * <p>检测策略：以 "&lt;" 开头判定为 XML，否则判定为 JSON。
     * 空内容抛出异常。
     *
     * @param payload 消息内容
     * @return "xml" 或 "json"
     */
    private String detectStructuredFormat(String payload) {
        String trimmed = payload == null ? "" : payload.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        return trimmed.startsWith("<") ? "xml" : "json"; // 简单启发式：首字符为 < 则为 XML
    }

    /**
     * 数据格式转换
     *
     * <p>将原始格式的消息转换为标准 JSON：
     * <ul>
     *   <li>text 格式：直接透传，不做转换</li>
     *   <li>json 格式：通过 DataConverter 转换为标准 JSON 后直接返回</li>
     *   <li>xml 格式：通过 DataConverter 转换为 JSON 后，额外封装 _meta 元信息
     *       （包含 source_format 和 converter 标识），便于接收方识别原始格式</li>
     * </ul>
     *
     * @param payload       原始消息内容
     * @param actualFormat  已确定的实际格式（json/xml/text）
     * @return 转换后的 JSON 字符串
     */
    private String convertPayload(String payload, String actualFormat) {
        if ("text".equals(actualFormat)) {
            return payload; // 纯文本无需转换，直接透传
        }

        // 从 DataConverter 列表中查找支持该格式的转换器
        DataConverter converter = dataConverters.stream()
                .filter(item -> item.supports(actualFormat))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "未找到可用的数据格式转换器: " + actualFormat));

        String convertedPayload = converter.convertToJson(payload);
        if (!"xml".equals(actualFormat)) {
            return convertedPayload; // JSON 格式转换后直接返回
        }

        // XML 格式额外封装 _meta 元信息，标记原始格式和转换器标识
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
