package com.cde.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonDataConverter implements DataConverter {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(String formatType) {
        return "json".equalsIgnoreCase(formatType);
    }

    @Override
    public String convertToJson(Object rawData) {
        try {
            if (rawData instanceof String) {
                // 校验是否为合法JSON
                mapper.readTree((String) rawData);
                return (String) rawData;
            }
            return mapper.writeValueAsString(rawData);
        } catch (Exception e) {
            throw new RuntimeException("JSON格式校验失败: " + e.getMessage());
        }
    }

    @Override
    public Object convertFromJson(String json, String targetFormat) {
        return json; // JSON→JSON 无需转换
    }
}
