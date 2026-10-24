package com.konselyavisa.payment.paydunya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LivePayDunyaGatewayTest {

    private static final String BASE = "https://paydunya.test";
    private static final String MASTER = "master-key";

    private PayDunyaProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private LivePayDunyaGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new PayDunyaProperties();
        properties.setMasterKey(MASTER);
        properties.setPrivateKey("private-key");
        properties.setToken("token");
        properties.setApiBaseUrl(BASE);
        properties.setStoreName("KonselyaVisa");
        properties.setCallbackUrl("https://app.test/callback");
        properties.setReturnUrl("https://app.test/return");
        properties.setCancelUrl("https://app.test/cancel");
        restClientBuilder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        gateway = new LivePayDunyaGateway(properties, restClientBuilder.build(), new ObjectMapper());
    }

    @ParameterizedTest
    @MethodSource("statusMappings")
    void mapStatusCoversRemoteValues(String remote, PaymentStatus expected) {
        assertThat(LivePayDunyaGateway.mapStatus(remote)).isEqualTo(expected);
    }

    static Stream<Arguments> statusMappings() {
        return Stream.of(
                Arguments.of("completed", PaymentStatus.COMPLETED),
                Arguments.of("COMPLETED", PaymentStatus.COMPLETED),
                Arguments.of("cancelled", PaymentStatus.FAILED),
                Arguments.of("canceled", PaymentStatus.FAILED),
                Arguments.of("failed", PaymentStatus.FAILED),
                Arguments.of("pending", PaymentStatus.PENDING),
                Arguments.of(null, PaymentStatus.PENDING),
                Arguments.of("  ", PaymentStatus.PENDING));
    }

    @Test
    void createInvoiceRequiresConfiguration() {
        LivePayDunyaGateway unconfigured =
                new LivePayDunyaGateway(new PayDunyaProperties(), mock(RestClient.class), new ObjectMapper());

        assertThatThrownBy(() -> unconfigured.createInvoice(order()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.paydunya_not_configured");
    }

    @Test
    void createInvoiceReturnsCheckoutSession() {
        server.expect(requestTo(BASE + "/v1/checkout-invoice/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PAYDUNYA-MASTER-KEY", MASTER))
                .andRespond(withSuccess(
                        "{\"response_code\":\"00\",\"token\":\"inv-token\",\"response_text\":\"https://pay.paydunya/inv\"}",
                        MediaType.APPLICATION_JSON));

        CheckoutSession session = gateway.createInvoice(order());

        assertThat(session.providerReference()).isEqualTo("inv-token");
        assertThat(session.checkoutUrl()).isEqualTo("https://pay.paydunya/inv");
        assertThat(session.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        server.verify();
    }

    @Test
    void createInvoiceFailsWhenProviderReturnsErrorCode() {
        server.expect(requestTo(BASE + "/v1/checkout-invoice/create"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"response_code\":\"01\",\"response_text\":\"error\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createInvoice(order()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.paydunya_unavailable");
    }

    @Test
    void confirmMapsCompletedStatus() {
        server.expect(requestTo(BASE + "/v1/checkout-invoice/confirm/inv-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"response_code\":\"00\",\"status\":\"completed\"}", MediaType.APPLICATION_JSON));

        PaymentStatusView view = gateway.confirm("inv-1");

        assertThat(view.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(view.providerReference()).isEqualTo("inv-1");
        server.verify();
    }

    @Test
    void parseWebhookRejectsInvalidHashWithoutCallingApi() {
        Map<String, Object> fields = Map.of(
                "data",
                Map.of(
                        "hash", "not-a-valid-hash",
                        "invoice", Map.of("token", "inv-1")));

        assertThatThrownBy(() -> gateway.parseWebhook(new WebhookPayload(fields, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_signature");
        server.verify();
    }

    @Test
    void parseWebhookReconfirmsStatusViaApi() {
        String hash = PayDunyaHash.ofMasterKey(MASTER);
        Map<String, Object> fields = Map.of(
                "data",
                Map.of(
                        "hash", hash,
                        "invoice", Map.of("token", "inv-ok")));
        server.expect(requestTo(BASE + "/v1/checkout-invoice/confirm/inv-ok"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"response_code\":\"00\",\"status\":\"completed\"}", MediaType.APPLICATION_JSON));

        ParsedWebhook parsed = gateway.parseWebhook(new WebhookPayload(fields, null, null));

        assertThat(parsed.providerReference()).isEqualTo("inv-ok");
        assertThat(parsed.completed()).isTrue();
        assertThat(parsed.ignored()).isFalse();
        assertThat(parsed.idempotencyKey()).isEqualTo("inv-ok:COMPLETED");
        server.verify();
    }

    private static PaymentOrder order() {
        return new PaymentOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ORD-1",
                "XOF",
                50000L);
    }
}
