package com.konselyavisa.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualPaymentProviderTest {

    private final ManualPaymentProvider provider = new ManualPaymentProvider();

    @Test
    void createCheckoutStaysPendingManualWithoutUrl() {
        UUID paymentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        CheckoutSession session = provider.createCheckout(new PaymentOrder(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), paymentId, "ORD-2", "XOF", 0));

        assertThat(session.providerReference()).isEqualTo("manual_" + paymentId);
        assertThat(session.checkoutUrl()).isNull();
        assertThat(session.paymentStatus()).isEqualTo(PaymentStatus.PENDING_MANUAL);
    }

    @Test
    void verifyAcceptsManualReferenceOnly() {
        assertThat(provider.verify("manual_1").status()).isEqualTo(PaymentStatus.PENDING_MANUAL);
        assertThatThrownBy(() -> provider.verify(""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.not_found");
        assertThatThrownBy(() -> provider.verify("mock_1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.not_found");
    }

    @Test
    void webhookIsRejected() {
        assertThatThrownBy(() -> provider.handleWebhook(new WebhookPayload(Map.of(), "sig")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.manual_webhook_unsupported");
    }
}
