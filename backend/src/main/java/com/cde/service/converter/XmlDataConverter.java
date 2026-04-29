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

/**
 * XML转JSON转换器，基于Jackson XmlMapper实现。
 * <p>
 * 核心处理流程：XML字符串 → Jackson XmlMapper解析为JsonNode → cleanXmlNodes清洗XML特有结构 → 输出标准JSON。
 * <p>
 * cleanXmlNodes负责移除XML序列化产生的特有产物：
 * <ul>
 *   <li>去除属性名的"@"前缀（XML属性在Jackson中被标记为@fieldName）</li>
 *   <li>处理"$"和"#text"键（XML文本内容和混合内容）</li>
 *   <li>当对象仅含文本内容时，简化为纯值节点</li>
 * </ul>
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

    /**
     * 清洗XML解析产生的特有节点结构，使其符合标准JSON格式。
     * <p>
     * 转换步骤：
     * <ol>
     *   <li>遍历对象的所有字段，去除字段名的"@"前缀（XML属性标记）</li>
     *   <li>递归处理每个字段的子节点</li>
     *   <li>处理"$"和"#text"键：若对象仅含文本内容，简化为纯值节点；否则移除文本键</li>
     *   <li>数组节点逐元素递归清洗</li>
     * </ol>
     *
     * @param node 待清洗的JsonNode
     * @return 清洗后的JsonNode
     */
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