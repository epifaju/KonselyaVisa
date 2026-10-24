package com.konselyavisa.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class N8nOutboxWebhookClientTest {

    private static final String URL = "https://n8n.test/hook";
    private static final String SECRET = "whsec-test";

    private OutboxProperties properties;
    private MockRestServiceServer server;
    private N8nOutboxWebhookClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setWebhookUrl(URL);
        properties.setWebhookSecret(SECRET);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new N8nOutboxWebhookClient(properties, objectMapper, builder.build());
    }

    @Test
    void sendPostsSignedPayload() throws Exception {
        OutboxWebhookMessage message = sampleMessage();
        byte[] body = objectMapper.writeValueAsBytes(message);
        String signature = "sha256=" + hmac(body, SECRET);
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Konselya-Event-Type", "CASE_CREATED"))
                .andExpect(header("X-Konselya-Signature", signature))
                .andRespond(withSuccess());

        client.send(message);
        server.verify();
    }

    @Test
    void sendMarksServerErrorsRetryable() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.send(sampleMessage()))
                .isInstanceOf(OutboxDispatchException.class)
                .satisfies(ex -> {
                    OutboxDispatchException dispatch = (OutboxDispatchException) ex;
                    org.assertj.core.api.Assertions.assertThat(dispatch.isRetryable()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(dispatch.getMessage()).contains("502");
                });
    }

    @Test
    void sendMarksClientErrorsNonRetryable() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.send(sampleMessage()))
                .isInstanceOf(OutboxDispatchException.class)
                .satisfies(ex ->
                        org.assertj.core.api.Assertions.assertThat(((OutboxDispatchException) ex).isRetryable())
                                .isFalse());
    }

    private static OutboxWebhookMessage sampleMessage() {
        return new OutboxWebhookMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CASE_CREATED",
                "CASE",
                UUID.randomUUID(),
                Instant.parse("2026-10-24T10:00:00Z"),
                Map.of("caseId", "c1"));
    }

    private static String hmac(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
