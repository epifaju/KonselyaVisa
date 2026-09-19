package com.konselyavisa.payment.provider;

import com.konselyavisa.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(provider -> provider.code().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public PaymentProvider require(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("error.payment.provider_unknown");
        }
        PaymentProvider provider = providers.get(code.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw BusinessException.badRequest("error.payment.provider_unknown");
        }
        return provider;
    }
}
