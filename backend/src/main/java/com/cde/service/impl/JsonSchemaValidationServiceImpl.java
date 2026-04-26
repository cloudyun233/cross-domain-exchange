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

@Service
@RequiredArgsConstructor
public class JsonSchemaValidationServiceImpl implements JsonSchemaValidationService {

    private final ObjectMapper objectMapper;

    private static final Map<String, Set<String>> REQUIRED_FIELDS_BY_TOPIC = Map.of(
            "cross_domain/gov/minzheng/population", Set.of("populationId", "name")
    );

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
