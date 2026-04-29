package com.cde.service.impl;

import com.cde.exception.BusinessException;
import com.cde.service.JsonSchemaValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * JSON Schema校验服务实现
 *
 * <p>按主题配置必填字段（{@link #REQUIRED_FIELDS_BY_TOPIC}），对JSON payload执行必填字段校验。
 * 采用软校验策略：校验失败时抛出BusinessException阻断当前请求，
 * 但在发布流程中由调用方捕获异常后仅记录审计事件，不阻断消息发布。</p>
 */
@Service
@RequiredArgsConstructor
public class JsonSchemaValidationServiceImpl implements JsonSchemaValidationService {

    private final ObjectMapper objectMapper;

    /** 主题级必填字段配置Map，key为主题路径，value为该主题下必填的字段名集合 */
    private static final Map<String, Set<String>> REQUIRED_FIELDS_BY_TOPIC = Map.of(
            "cross_domain/gov/minzheng/population", Set.of("populationId", "name")
    );

    /**
     * 校验JSON payload是否符合主题对应的Schema要求
     *
     * <p>校验流程：
     * 1. 文本格式(actualFormat=text)直接跳过，不执行校验；
     * 2. 查找主题对应的必填字段配置，无配置则跳过；
     * 3. 解析JSON，优先取data子对象作为校验目标，否则取根对象；
     * 4. 逐字段检查是否非null且存在，缺失则抛出BusinessException。</p>
     *
     * @param topic        主题路径
     * @param payload      消息体JSON字符串
     * @param actualFormat 实际格式（text/json）
     */
    @Override
    public void validate(String topic, String payload, String actualFormat) {
        if ("text".equalsIgnoreCase(actualFormat)) {
            return;
        }

        Set<String> requiredFields = REQUIRED_FIELDS_BY_TOPIC.get(topic);
        if (requiredFields == null) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode target = root.has("data") && root.get("data").isObject() ? root.get("data") : root;
            for (String field : requiredFields) {
                if (!target.hasNonNull(field)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "JSON Schema 校验失败: 缺少必填字段 " + field);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "JSON Schema 校验失败: " + e.getMessage());
        }
    }
}
