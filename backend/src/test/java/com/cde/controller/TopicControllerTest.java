package com.cde.controller;

import com.cde.exception.BusinessException;
import com.cde.service.AuditService;
import com.cde.service.JsonSchemaValidationService;
import com.cde.service.TopicService;
import com.cde.service.converter.DataConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopicControllerTest {

    @Mock
    private TopicService topicService;

    @Mock
    private AuditService auditService;

    @Mock
    private JsonSchemaValidationService jsonSchemaValidationService;

    @Test
    void publishRecordsAuditAndSkipsBrokerWhenJsonConversionFails() {
        TopicController controller = new TopicController(
                topicService,
                List.of(new FailingJsonConverter()),
                new ObjectMapper(),
                auditService,
                jsonSchemaValidationService
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);

        assertThatThrownBy(() -> controller.publish(
                "cross_domain/medical/swh",
                "{invalid-json",
                1,
                false,
                "json",
                "Bearer token",
                auth
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("format_convert_fail"),
                detailCaptor.capture(),
                org.mockito.Mockito.eq("0.0.0.0")
        );
        assertThat(detailCaptor.getValue()).contains("cross_domain/medical/swh", "json", "JSON格式校验失败");
        verify(topicService, never()).publishMsg(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.anyBoolean(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString()
        );
    }

    @Test
    void publishRecordsAuditAndSkipsBrokerWhenSchemaValidationFails() {
        TopicController controller = new TopicController(
                topicService,
                List.of(new ValidJsonConverter()),
                new ObjectMapper(),
                auditService,
                jsonSchemaValidationService
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);
        doThrow(new BusinessException(HttpStatus.BAD_REQUEST, "JSON Schema 校验失败: 缺少必填字段 populationId"))
                .when(jsonSchemaValidationService)
                .validate("cross_domain/gov/minzheng/population", "{\"name\":\"张三\"}", "json");

        assertThatThrownBy(() -> controller.publish(
                "cross_domain/gov/minzheng/population",
                "{\"name\":\"张三\"}",
                1,
                false,
                "json",
                "Bearer token",
                auth
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("format_convert_fail"),
                detailCaptor.capture(),
                org.mockito.Mockito.eq("0.0.0.0")
        );
        assertThat(detailCaptor.getValue()).contains("cross_domain/gov/minzheng/population", "json", "JSON Schema 校验失败");
        verify(topicService, never()).publishMsg(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.anyBoolean(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString()
        );
    }

    private static class FailingJsonConverter implements DataConverter {
        @Override
        public boolean supports(String formatType) {
            return "json".equalsIgnoreCase(formatType);
        }

        @Override
        public String convertToJson(Object rawData) {
            throw new RuntimeException("JSON格式校验失败: mock failure");
        }

        @Override
        public Object convertFromJson(String json, String targetFormat) {
            return json;
        }
    }

    private static class ValidJsonConverter implements DataConverter {
        @Override
        public boolean supports(String formatType) {
            return "json".equalsIgnoreCase(formatType);
        }

        @Override
        public String convertToJson(Object rawData) {
            return rawData.toString();
        }

        @Override
        public Object convertFromJson(String json, String targetFormat) {
            return json;
        }
    }
}
