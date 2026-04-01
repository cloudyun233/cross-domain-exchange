package com.cde.service.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

/**
 * XML数据格式转换器 (论文4.4.1: XML→JSON转换)
 */
@Component
public class XmlDataConverter implements DataConverter {

    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public boolean supports(String formatType) {
        return "xml".equalsIgnoreCase(formatType);
    }

    @Override
    public String convertToJson(Object rawData) {
        try {
            String xml = rawData.toString();
            JsonNode node = xmlMapper.readTree(xml.getBytes());
            return jsonMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("XML→JSON转换失败: " + e.getMessage());
        }
    }

    @Override
    public Object convertFromJson(String json, String targetFormat) {
        try {
            JsonNode node = jsonMapper.readTree(json);
            return xmlMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("JSON→XML转换失败: " + e.getMessage());
        }
    }
}
