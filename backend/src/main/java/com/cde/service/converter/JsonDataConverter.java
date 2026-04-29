package com.cde.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * JSON直通转换器，负责JSON格式数据的校验与透传。
 * <p>
 * convertToJson：解析并重新序列化，确保输入为合法JSON。
 * convertFromJson：恒等操作，JSON到JSON无需转换。
 */
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
