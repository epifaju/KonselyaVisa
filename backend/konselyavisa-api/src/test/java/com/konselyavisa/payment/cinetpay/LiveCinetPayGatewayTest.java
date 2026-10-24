package com.konselyavisa.payment.cinetpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LiveCinetPayGatewayTest {

    private static final String BASE = "https://cinetpay.test";
    private static final String SECRET = "live-secret";

    private CinetPayProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private LiveCinetPayGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new CinetPayProperties();
        properties.setApiKey("api-key");
        properties.setSiteId("site-1");
        properties.setSecretKey(SECRET);
        properties.setApiBaseUrl(BASE);
        properties.setNotifyUrl("https://app.test/notify");
        properties.setReturnUrl("https://app.test/return");
        restClientBuilder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        gateway = new LiveCinetPayGateway(properties, restClientBuilder.build());
    }

    @Test
    void createPaymentRequiresConfiguration() {
        LiveCinetPayGateway unconfigured =
                new LiveCinetPayGateway(new CinetPayProperties(), mock(RestClient.class));

        assertThatThrownBy(() -> unconfigured.createPayment(order()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.cinetpay_not_configured");
    }

    @Test
    void createPaymentReturnsCheckoutSession() {
        UUID paymentId = UUID.randomUUID();
        server.expect(requestTo(BASE + "/v2/payment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":\"201\",\"data\":{\"payment_url\":\"https://pay.cinetpay/checkout\"}}",
                        MediaType.APPLICATION_JSON));

        CheckoutSession session = gateway.createPayment(order(paymentId));

        assertThat(session.providerReference()).isEqualTo(paymentId.toString());
        assertThat(session.checkoutUrl()).isEqualTo("https://pay.cinetpay/checkout");
        assertThat(session.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        server.verify();
    }

    @Test
    void createPaymentFailsWhenProviderReturnsBadCode() {
        server.expect(requestTo(BASE + "/v2/payment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":\"400\",\"message\":\"nope\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createPayment(order()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.cinetpay_unavailable");
    }

    @Test
    void checkTransactionMapsAcceptedStatus() {
        String tx = "pay-check-1";
        server.expect(requestTo(BASE + "/v2/payment/check"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"code\":\"00\",\"data\":{\"status\":\"ACCEPTED\"}}", MediaType.APPLICATION_JSON));

        PaymentStatusView view = gateway.checkTransaction(tx);

        assertThat(view.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(view.providerReference()).isEqualTo(tx);
        server.verify();
    }

    @Test
    void parseWebhookRejectsInvalidSignatureWithoutCallingApi() {
        Map<String, Object> fields = CinetPayHmacTest.notifyFields("pay-1");
        fields.put("signature", "deadbeef");

        assertThatThrownBy(() -> gateway.parseWebhook(new WebhookPayload(fields, "deadbeef", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_signature");
        server.verify();
    }

    @Test
    void parseWebhookRequiresWebhookSecret() {
        LiveCinetPayGateway unconfigured =
                new LiveCinetPayGateway(new CinetPayProperties(), mock(RestClient.class));

        assertThatThrownBy(() -> unconfigured.parseWebhook(new WebhookPayload(Map.of(), null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.cinetpay_not_configured");
    }

    @Test
    void parseWebhookReconfirmsStatusViaApi() {
        Map<String, Object> fields = CinetPayHmacTest.notifyFields("pay-ok");
        String signature = CinetPayHmac.sign(fields, SECRET);
        fields.put("signature", signature);
        server.expect(requestTo(BASE + "/v2/payment/check"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"data\":{\"status\":\"ACCEPTED\"}}", MediaType.APPLICATION_JSON));

        ParsedWebhook parsed = gateway.parseWebhook(new WebhookPayload(fields, signature, null));

        assertThat(parsed.providerReference()).isEqualTo("pay-ok");
        assertThat(parsed.completed()).isTrue();
        assertThat(parsed.ignored()).isFalse();
        assertThat(parsed.idempotencyKey()).isEqualTo("pay-ok:COMPLETED");
        server.verify();
    }

    @Test
    void parseWebhookIgnoresPendingRemoteStatus() {
        Map<String, Object> fields = CinetPayHmacTest.notifyFields("pay-pending");
        String signature = CinetPayHmac.sign(fields, SECRET);
        fields.put("signature", signature);
        server.expect(requestTo(BASE + "/v2/payment/check"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"data\":{\"status\":\"WAITING\"}}", MediaType.APPLICATION_JSON));

        ParsedWebhook parsed = gateway.parseWebhook(new WebhookPayload(fields, signature, null));

        assertThat(parsed.ignored()).isTrue();
        assertThat(parsed.completed()).isFalse();
        server.verify();
    }

    private static PaymentOrder order() {
        return order(UUID.randomUUID());
    }

    private static PaymentOrder order(UUID paymentId) {
        return new PaymentOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                paymentId,
                "ORD-1",
                "EUR",
                8500L);
    }
}
