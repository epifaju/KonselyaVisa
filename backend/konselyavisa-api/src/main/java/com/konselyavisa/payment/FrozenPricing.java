package com.konselyavisa.payment;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Locale;
import java.util.Map;

public record FrozenPricing(String currency, long amountMinor) {

    public static FrozenPricing from(Map<String, Object> pricing) {
        if (pricing == null || pricing.isEmpty()) {
            throw BusinessException.badRequest("error.order.pricing_missing");
        }
        Object currencyRaw = pricing.get("currency");
        Object amountRaw = pricing.get("amountMinor");
        if (!(currencyRaw instanceof String currency) || currency.isBlank() || currency.length() != 3) {
            throw BusinessException.badRequest("error.order.pricing_invalid");
        }
        if (!(amountRaw instanceof Number amount) || amount.longValue() <= 0) {
            throw BusinessException.badRequest("error.order.pricing_invalid");
        }
        return new FrozenPricing(currency.toUpperCase(Locale.ROOT), amount.longValue());
    }
}
