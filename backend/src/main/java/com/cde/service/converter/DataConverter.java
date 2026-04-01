package com.cde.service.converter;

/**
 * 数据格式转换器接口 (论文4.4.3: DataConverter)
 * 遵循开闭原则，新增格式只需新建实现类
 */
public interface DataConverter {
    boolean supports(String formatType);
    String convertToJson(Object rawData);
    Object convertFromJson(String json, String targetFormat);
}
