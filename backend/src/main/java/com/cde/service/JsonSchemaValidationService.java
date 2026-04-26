package com.cde.service;

public interface JsonSchemaValidationService {
    void validate(String topic, String payload, String actualFormat);
}
