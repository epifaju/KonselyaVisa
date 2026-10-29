package com.konselyavisa.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentProviderRegistryTest {

    @Test
    void requireResolvesCodeIgnoringCase() {
        MockPaymentProvider mock = new MockPaymentProvider();
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(mock, new ManualPaymentProvider()));

        assertThat(registry.require("mock")).isSameAs(mock);
        assertThat(registry.require("MANUAL").code()).isEqualTo(PaymentProviderCodes.MANUAL);
    }

    @Test
    void requireRejectsBlankUnknownAndNullCodes() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(new MockPaymentProvider()));

        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.provider_unknown");
        assertThatThrownBy(() -> registry.require("  "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.provider_unknown");
        assertThatThrownBy(() -> registry.require("STRIPE"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.provider_unknown");
    }

    @Test
    void duplicateCodesFailFast() {
        PaymentProvider first = stub("MOCK");
        PaymentProvider second = stub("mock");

        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static PaymentProvider stub(String code) {
        return new PaymentProvider() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public CheckoutSession createCheckout(PaymentOrder order) {
                return new CheckoutSession(code, null, PaymentStatus.PENDING);
            }

            @Override
            public PaymentStatusView verify(String providerReference) {
                return new PaymentStatusView(PaymentStatus.PENDING, providerReference);
            }

            @Override
            public ParsedWebhook handleWebhook(WebhookPayload payload) {
                return new ParsedWebhook("ref", "key", false);
            }
        };
    }
}
