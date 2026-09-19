package com.konselyavisa.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class N8nOutboxWebhookClient implements OutboxWebhookClient {

    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public N8nOutboxWebhookClient(OutboxProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.getRequestTimeout();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void send(OutboxWebhookMessage message) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException ex) {
            throw new OutboxDispatchException("error.outbox.serialize", false, ex);
        }
        try {
            restClient
                    .post()
                    .uri(URI.create(properties.getWebhookUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.set("X-Konselya-Event-Type", message.eventType());
                        headers.set("X-Konselya-Event-Id", message.id().toString());
                        String signature = signature(body);
                        if (signature != null) {
                            headers.set("X-Konselya-Signature", signature);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = status >= 500 || status == 404 || status == 408 || status == 429;
            throw new OutboxDispatchException("HTTP " + status, retryable, ex);
        } catch (ResourceAccessException ex) {
            throw new OutboxDispatchException("HTTP timeout", true, ex);
        }
    }

    private String signature(byte[] body) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new OutboxDispatchException("error.outbox.signature", false, ex);
        }
    }
}
