package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagKeysTest {

    @Test
    void procedureAndPaymentProviderKeysAreStable() {
        assertThat(FeatureFlagKeys.procedure("LEGALIZATION_FR_GW")).isEqualTo("procedure.LEGALIZATION_FR_GW");
        assertThat(FeatureFlagKeys.paymentProvider("stripe")).isEqualTo("payment.provider.STRIPE");
        assertThat(FeatureFlagKeys.paymentProvider("CINETPAY")).isEqualTo("payment.provider.CINETPAY");
    }
}
