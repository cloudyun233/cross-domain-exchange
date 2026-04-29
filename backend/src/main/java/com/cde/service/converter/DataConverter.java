package com.cde.service.converter;

/**
 * 数据格式转换器接口 (论文4.4.3: DataConverter)。
 * <p>
 * 采用策略模式设计，每种数据格式对应一个实现类，运行时根据formatType选择转换器。
 * 遵循开闭原则：新增格式只需新建实现类并注册为Spring Bean，无需修改已有代码。
 * <p>
 * 方法说明：
 * <ul>
 *   <li>convertToJson — 将原始格式数据转为JSON字符串</li>
 *   <li>convertFromJson — 将JSON字符串转为目标格式，预留用于后续反向转换扩展</li>
 * </ul>
 */
public interface DataConverter {
    boolean supports(String formatType);
    String convertToJson(Object rawData);
    Object convertFromJson(String json, String targetFormat);//用于后续扩展
}
