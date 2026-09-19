package com.konselyavisa.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.konselyavisa.organization.FeatureFlagService;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentProviderAccessGuardTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private PaymentProviderAccessGuard guard;

    @Test
    void mockIsNeverGated() {
        assertThat(guard.resolveEnabledCode(null)).isEqualTo(PaymentProviderCodes.MOCK);
        assertThat(guard.resolveEnabledCode("mock")).isEqualTo(PaymentProviderCodes.MOCK);
    }

    @Test
    void disabledStripeFallsBackToMock() {
        when(featureFlagService.isPaymentProviderEnabled(PaymentProviderCodes.STRIPE)).thenReturn(false);
        assertThat(guard.resolveEnabledCode("STRIPE")).isEqualTo(PaymentProviderCodes.MOCK);
    }

    @Test
    void enabledStripeIsKept() {
        when(featureFlagService.isPaymentProviderEnabled(PaymentProviderCodes.STRIPE)).thenReturn(true);
        assertThat(guard.resolveEnabledCode("stripe")).isEqualTo(PaymentProviderCodes.STRIPE);
    }

    @Test
    void enabledCinetPayIsKept() {
        when(featureFlagService.isPaymentProviderEnabled(PaymentProviderCodes.CINETPAY)).thenReturn(true);
        assertThat(guard.resolveEnabledCode("cinetpay")).isEqualTo(PaymentProviderCodes.CINETPAY);
    }
}
