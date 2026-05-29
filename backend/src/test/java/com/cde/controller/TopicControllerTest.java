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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicControllerTest {

    @Mock
    private TopicService topicService;

    @Mock
    private AuditService auditService;

    @Mock
    private JsonSchemaValidationService jsonSchemaValidationService;

    @Test
    void getTopicTreeReturnsServiceData() {
        TopicController controller = controller(List.of());
        when(topicService.buildDomainTopicTree()).thenReturn(List.of());

        assertThat(controller.getTopicTree().isSuccess()).isTrue();
        assertThat(controller.getTopicTree().getData()).isEmpty();
    }

    @Test
    void publishStructuredJsonAndTextPayloads() {
        TopicController controller = controller(List.of(new ValidJsonConverter()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);

        controller.publish(
                "cross_domain/medical/swh",
                "{\"name\":\"alice\"}",
                1,
                false,
                "structured",
                "Bearer token",
                auth
        );
        controller.publish(
                "cross_domain/medical/swh",
                "plain text",
                0,
                false,
                "text",
                "Bearer token",
                auth
        );

        verify(topicService).publishMsg(
                org.mockito.Mockito.eq("cross_domain/medical/swh"),
                org.mockito.Mockito.eq("{\"name\":\"alice\"}"),
                org.mockito.Mockito.eq(1),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("token")
        );
        verify(topicService).publishMsg(
                org.mockito.Mockito.eq("cross_domain/medical/swh"),
                org.mockito.Mockito.eq("plain text"),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("token")
        );
    }

    @Test
    void publishStructuredXmlWrapsConvertedJsonWithMetadata() {
        TopicController controller = controller(List.of(new ValidXmlConverter()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);

        controller.publish(
                "cross_domain/medical/swh",
                "<root/>",
                1,
                true,
                null,
                "Bearer token",
                auth
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(topicService).publishMsg(
                org.mockito.Mockito.eq("cross_domain/medical/swh"),
                payloadCaptor.capture(),
                org.mockito.Mockito.eq(1),
                org.mockito.Mockito.eq(true),
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("token")
        );
        assertThat(payloadCaptor.getValue()).contains("_meta", "source_format", "xml", "data");
    }

    @Test
    void publishRejectsInvalidStructuredFormatAndMissingConverter() {
        TopicController controller = controller(List.of());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);

        assertThatThrownBy(() -> controller.publish(
                "cross_domain/medical/swh",
                "",
                1,
                false,
                "structured",
                "Bearer token",
                auth
        )).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> controller.publish(
                "cross_domain/medical/swh",
                "{}",
                1,
                false,
                "csv",
                "Bearer token",
                auth
        )).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> controller.publish(
                "cross_domain/medical/swh",
                "{}",
                1,
                false,
                "json",
                "Bearer token",
                auth
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void publishRejectsXmlWhenConvertedPayloadCannotBeWrapped() {
        TopicController controller = controller(List.of(new InvalidXmlConverter()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);

        assertThatThrownBy(() -> controller.publish(
                "cross_domain/medical/swh",
                "<root/>",
                1,
                false,
                "xml",
                "Bearer token",
                auth
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void publishRecordsAuditAndSkipsBrokerWhenJsonConversionFails() {
        TopicController controller = controller(List.of(new FailingJsonConverter()));
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
    void publishRecordsAuditAndStillPublishesWhenSchemaValidationFails() {
        TopicController controller = controller(List.of(new ValidJsonConverter()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("producer_medical_swh", null);
        doThrow(new BusinessException(HttpStatus.BAD_REQUEST, "JSON Schema 校验失败: 缺少必填字段 populationId"))
                .when(jsonSchemaValidationService)
                .validate("cross_domain/gov/minzheng/population", "{\"name\":\"张三\"}", "json");

        controller.publish(
                "cross_domain/gov/minzheng/population",
                "{\"name\":\"张三\"}",
                1,
                false,
                "json",
                "Bearer token",
                auth
        );

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("json_schema_validate_fail"),
                detailCaptor.capture(),
                org.mockito.Mockito.eq("0.0.0.0")
        );
        assertThat(detailCaptor.getValue()).contains("cross_domain/gov/minzheng/population", "json", "JSON Schema 校验失败");
        verify(topicService).publishMsg(
                org.mockito.Mockito.eq("cross_domain/gov/minzheng/population"),
                org.mockito.Mockito.eq("{\"name\":\"张三\"}"),
                org.mockito.Mockito.eq(1),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq("producer_medical_swh"),
                org.mockito.Mockito.eq("token")
        );
    }

    private TopicController controller(List<DataConverter> converters) {
        return new TopicController(
                topicService,
                converters,
                new ObjectMapper(),
                auditService,
                jsonSchemaValidationService
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

    private static class ValidXmlConverter implements DataConverter {
        @Override
        public boolean supports(String formatType) {
            return "xml".equalsIgnoreCase(formatType);
        }

        @Override
        public String convertToJson(Object rawData) {
            return "{\"value\":\"ok\"}";
        }

        @Override
        public Object convertFromJson(String json, String targetFormat) {
            return json;
        }
    }

    private static class InvalidXmlConverter implements DataConverter {
        @Override
        public boolean supports(String formatType) {
            return "xml".equalsIgnoreCase(formatType);
        }

        @Override
        public String convertToJson(Object rawData) {
            return "not-json";
        }

        @Override
        public Object convertFromJson(String json, String targetFormat) {
            return json;
        }
    }
}
