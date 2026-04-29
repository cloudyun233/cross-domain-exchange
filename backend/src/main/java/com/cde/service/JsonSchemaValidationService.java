package com.cde.service;

/**
 * JSON Schema校验服务接口
 *
 * <p>提供主题级别的JSON Schema校验。校验失败仅记录审计事件，
 * 不阻断消息发布（软校验策略），兼顾数据质量与系统可用性。</p>
 */
public interface JsonSchemaValidationService {
    void validate(String topic, String payload, String actualFormat);
}
