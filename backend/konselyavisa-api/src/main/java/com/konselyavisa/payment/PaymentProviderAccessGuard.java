package com.konselyavisa.payment;

import com.konselyavisa.organization.FeatureFlagService;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Settings still choose which provider to use; {@code feature_flags} decide whether that
 * provider is offered. A disabled provider falls back to MOCK (checkout must not fail
 * because a rollout flag is off). MOCK itself is never gated.
 */
@Component
public class PaymentProviderAccessGuard {

    private final FeatureFlagService featureFlagService;

    public PaymentProviderAccessGuard(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    public String resolveEnabledCode(String requestedCode) {
        if (requestedCode == null || requestedCode.isBlank()) {
            return PaymentProviderCodes.MOCK;
        }
        String code = requestedCode.toUpperCase(Locale.ROOT);
        if (PaymentProviderCodes.MOCK.equals(code)) {
            return PaymentProviderCodes.MOCK;
        }
        if (featureFlagService.isPaymentProviderEnabled(code)) {
            return code;
        }
        return PaymentProviderCodes.MOCK;
    }
}
