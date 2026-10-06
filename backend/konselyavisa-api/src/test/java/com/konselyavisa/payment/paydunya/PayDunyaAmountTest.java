package com.konselyavisa.payment.paydunya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class PayDunyaAmountTest {

    @Test
    void convertsMinorUnitsForEuroAndLeavesXofAsIs() {
        assertThat(PayDunyaAmount.toMajorUnits("EUR", 5000)).isEqualTo(50);
        assertThat(PayDunyaAmount.toMajorUnits("XOF", 5000)).isEqualTo(5000);
    }

    @Test
    void rejectsFractionalEuroCents() {
        assertThatThrownBy(() -> PayDunyaAmount.toMajorUnits("EUR", 105))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.paydunya_amount_invalid");
    }
}
