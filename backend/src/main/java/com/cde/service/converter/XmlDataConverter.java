package com.cde.service.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
            // 1. 强制 UTF-8 编码，避免乱码
            String xml = rawData instanceof byte[] 
                ? new String((byte[]) rawData, StandardCharsets.UTF_8) 
                : rawData.toString();
            // 2. 直接传 String，不用 getBytes()，更安全
            JsonNode root = xmlMapper.readTree(xml);
            JsonNode cleaned = cleanXmlNodes(root);
            return jsonMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new RuntimeException("XML→JSON转换失败: " + e.getMessage(), e);
        }
    }

    private JsonNode cleanXmlNodes(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // 先收集所有字段名，避免迭代中修改集合
            List<String> fieldNames = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fieldNames::add);

            for (String name : fieldNames) {
                JsonNode child = obj.get(name);
                obj.remove(name);
                // 去掉 XML 属性的 @ 前缀
                String cleanName = name.startsWith("@") ? name.substring(1) : name;
                obj.set(cleanName, cleanXmlNodes(child));
            }

            // 处理 XML 文本内容（$ 或 #text）
            String[] textKeys = {"$", "#text"};
            for (String textKey : textKeys) {
                if (obj.has(textKey)) {
                    JsonNode textNode = obj.get(textKey);
                    obj.remove(textKey);
                    if (obj.isEmpty()) {
                        return textNode;
                    }
                    // 【可选】如果需要保留文本内容，取消下面注释
                    // obj.set("text", textNode);
                }
            }

            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, cleanXmlNodes(arr.get(i)));
            }
            return arr;
        }
        return node;
    }

    @Override
    public Object convertFromJson(String json, String targetFormat) {
        try {
            JsonNode node = jsonMapper.readTree(json);
            // 指定根元素名为 root，避免默认的 ObjectNode
            return xmlMapper.writer().withRootName("root").writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("JSON→XML转换失败: " + e.getMessage(), e);
        }
    }
}