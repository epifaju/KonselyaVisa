package com.konselyavisa.payment.paydunya;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayDunyaIpnPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsNestedDataMap() {
        Map<String, Object> fields = Map.of("data", Map.of("status", "completed", "hash", "abc"));
        assertThat(PayDunyaIpnPayload.extract(fields, objectMapper))
                .containsEntry("status", "completed")
                .containsEntry("hash", "abc");
    }

    @Test
    void extractsJsonStringData() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("status", "completed", "hash", "xyz"));
        Map<String, Object> fields = Map.of("data", json);
        assertThat(PayDunyaIpnPayload.extract(fields, objectMapper)).containsEntry("hash", "xyz");
    }

    @Test
    void extractsBracketNotation() {
        Map<String, Object> fields = Map.of(
                "data[hash]", "abc",
                "data[status]", "completed",
                "data[invoice][token]", "test_token");
        Map<String, Object> data = PayDunyaIpnPayload.extract(fields, objectMapper);
        assertThat(data.get("hash")).isEqualTo("abc");
        assertThat(data.get("status")).isEqualTo("completed");
        assertThat(data.get("invoice")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) data.get("invoice");
        assertThat(invoice.get("token")).isEqualTo("test_token");
    }

    @Test
    void returnsEmptyWhenBodyIsMissingOrUnusable() {
        assertThat(PayDunyaIpnPayload.extract(null, objectMapper)).isEmpty();
        assertThat(PayDunyaIpnPayload.extract(Map.of(), objectMapper)).isEmpty();
        assertThat(PayDunyaIpnPayload.extract(Map.of("data", "completed"), objectMapper)).isEmpty();
        assertThat(PayDunyaIpnPayload.extract(Map.of("data", "{"), objectMapper)).isEmpty();
        assertThat(PayDunyaIpnPayload.extract(Map.of("other", "value"), objectMapper)).isEmpty();
    }

    @Test
    void acceptsFlatStatusAndReusesNestedMaps() {
        assertThat(PayDunyaIpnPayload.extract(Map.of("status", "completed"), objectMapper))
                .containsEntry("status", "completed");
        assertThat(PayDunyaIpnPayload.extract(Map.of("hash", "abc"), objectMapper)).containsEntry("hash", "abc");
        assertThat(PayDunyaIpnPayload.extract(Map.of("invoice", "inv-1"), objectMapper))
                .containsEntry("invoice", "inv-1");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(null, "ignored");
        fields.put("data[invoice][token]", "tok");
        fields.put("data[invoice][total]", "1000");
        fields.put("data[]", "blank");
        Map<String, Object> data = PayDunyaIpnPayload.extract(fields, objectMapper);
        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) data.get("invoice");
        assertThat(invoice).containsEntry("token", "tok").containsEntry("total", "1000");

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put(null, "skip");
        raw.put("status", "completed");
        assertThat(PayDunyaIpnPayload.extract(Map.of("data", raw), objectMapper))
                .containsEntry("status", "completed")
                .doesNotContainKey("null");
    }
}
