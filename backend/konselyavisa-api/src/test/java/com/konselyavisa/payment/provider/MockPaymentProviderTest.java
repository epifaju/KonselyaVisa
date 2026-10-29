package com.konselyavisa.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider = new MockPaymentProvider();

    @Test
    void createCheckoutReturnsPendingMockReference() {
        UUID paymentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        CheckoutSession session = provider.createCheckout(order(paymentId));

        assertThat(session.providerReference()).isEqualTo("mock_" + paymentId);
        assertThat(session.checkoutUrl()).contains(session.providerReference());
        assertThat(session.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void verifyRejectsUnknownReference() {
        assertThat(provider.verify("mock_ok").status()).isEqualTo(PaymentStatus.PENDING);
        assertThatThrownBy(() -> provider.verify(null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.not_found");
        assertThatThrownBy(() -> provider.verify("stripe_1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.not_found");
    }

    @Test
    void webhookRequiresReferenceAndIdempotencyKey() {
        ParsedWebhook completed = provider.handleWebhook(new WebhookPayload(
                Map.of("providerReference", "mock_1", "idempotencyKey", "evt-1", "event", "payment.completed"),
                null));
        assertThat(completed.completed()).isTrue();
        assertThat(completed.ignored()).isFalse();

        ParsedWebhook pending = provider.handleWebhook(
                new WebhookPayload(Map.of("providerReference", "mock_1", "idempotencyKey", "evt-2", "event", "other"), null));
        assertThat(pending.completed()).isFalse();

        assertThatThrownBy(() -> provider.handleWebhook(new WebhookPayload(Map.of("idempotencyKey", "evt"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_invalid");
        assertThatThrownBy(() -> provider.handleWebhook(
                        new WebhookPayload(Map.of("providerReference", "mock_1"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.idempotency_required");
        assertThatThrownBy(() -> provider.handleWebhook(new WebhookPayload(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_invalid");
    }

    private static PaymentOrder order(UUID paymentId) {
        return new PaymentOrder(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), paymentId, "ORD-1", "EUR", 1500);
    }
}
