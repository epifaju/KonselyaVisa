package com.konselyavisa.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrozenPricingTest {

    @Test
    void readsCatalogPricing() {
        FrozenPricing pricing = FrozenPricing.from(Map.of("currency", "eur", "amountMinor", 8500));
        assertThat(pricing.currency()).isEqualTo("EUR");
        assertThat(pricing.amountMinor()).isEqualTo(8500L);
    }

    @Test
    void rejectsMissingPricing() {
        assertThatThrownBy(() -> FrozenPricing.from(Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.order.pricing_missing");
    }
}
