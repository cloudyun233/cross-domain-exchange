package com.cde.service.converter;

import com.cde.exception.BusinessException;
import com.cde.service.impl.JsonSchemaValidationServiceImpl;
import com.cde.util.MqttTopicUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConvertersAndValidationTest {

    @Test
    void jsonConverterValidatesStringsAndSerializesObjects() {
        JsonDataConverter converter = new JsonDataConverter(new ObjectMapper());

        assertThat(converter.supports("JSON")).isTrue();
        assertThat(converter.convertToJson("{\"a\":1}")).isEqualTo("{\"a\":1}");
        assertThat(converter.convertToJson(Map.of("a", 1))).contains("\"a\":1");
        assertThat(converter.convertFromJson("{\"a\":1}", "json")).isEqualTo("{\"a\":1}");
        assertThatThrownBy(() -> converter.convertToJson("{bad"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void xmlConverterCleansAttributesTextNodesArraysAndBytes() {
        XmlDataConverter converter = new XmlDataConverter();

        assertThat(converter.supports("XML")).isTrue();
        assertThat(converter.convertToJson("<root id=\"1\"><name>alice</name><item>A</item><item>B</item></root>"))
                .contains("\"id\":\"1\"", "\"name\":\"alice\"", "\"item\":[\"A\",\"B\"]");
        assertThat(converter.convertToJson("<root><name>bob</name></root>".getBytes(StandardCharsets.UTF_8)))
                .contains("\"name\":\"bob\"");
        assertThat(converter.convertFromJson("{\"name\":\"alice\"}", "xml").toString())
                .contains("<root>");
        assertThatThrownBy(() -> converter.convertToJson("<root>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("XML");
    }

    @Test
    void jsonSchemaValidationCoversTextUnknownValidMissingAndInvalidJson() {
        JsonSchemaValidationServiceImpl service = new JsonSchemaValidationServiceImpl(new ObjectMapper());

        service.validate("cross_domain/gov/minzheng/population", "not-json", "text");
        service.validate("unknown", "not-json", "json");
        service.validate("cross_domain/gov/minzheng/population",
                "{\"data\":{\"populationId\":\"p1\",\"name\":\"alice\"}}", "json");

        assertThatThrownBy(() -> service.validate("cross_domain/gov/minzheng/population", "{\"name\":\"alice\"}", "json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("populationId");
        assertThatThrownBy(() -> service.validate("cross_domain/gov/minzheng/population", "{bad", "json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON Schema");
    }

    @Test
    void mqttTopicMatchingFollowsMqttWildcards() {
        assertThat(MqttTopicUtil.matchesTopic("cross/domain", "cross/domain")).isTrue();
        assertThat(MqttTopicUtil.matchesTopic("#", "anything/here")).isTrue();
        assertThat(MqttTopicUtil.matchesTopic("cross/+/data", "cross/gov/data")).isTrue();
        assertThat(MqttTopicUtil.matchesTopic("cross/#", "cross/gov/data")).isTrue();
        assertThat(MqttTopicUtil.matchesTopic("cross/+/data", "cross/gov/other")).isFalse();
        assertThat(MqttTopicUtil.matchesTopic("cross/gov/data", "cross/gov")).isFalse();
        assertThat(MqttTopicUtil.matchesTopic("cross/gov", "cross/gov/data")).isFalse();
    }
}
