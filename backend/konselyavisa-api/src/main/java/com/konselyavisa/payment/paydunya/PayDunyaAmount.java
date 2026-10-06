package com.konselyavisa.payment.paydunya;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Locale;
import java.util.Set;

public final class PayDunyaAmount {

    private static final Set<String> ZERO_DECIMAL = Set.of("XOF", "XAF", "GNF", "JPY", "KRW");

    private PayDunyaAmount() {}

    /** PayDunya expects {@code total_amount} in major currency units. */
    public static int toMajorUnits(String currency, long amountMinor) {
        if (currency == null || currency.isBlank()) {
            throw BusinessException.badRequest("error.payment.paydunya_amount_invalid");
        }
        String code = currency.toUpperCase(Locale.ROOT);
        long major;
        if (ZERO_DECIMAL.contains(code)) {
            major = amountMinor;
        } else {
            if (amountMinor % 100 != 0) {
                throw BusinessException.badRequest("error.payment.paydunya_amount_invalid");
            }
            major = amountMinor / 100;
        }
        if (major <= 0 || major > Integer.MAX_VALUE) {
            throw BusinessException.badRequest("error.payment.paydunya_amount_invalid");
        }
        return (int) major;
    }
}
