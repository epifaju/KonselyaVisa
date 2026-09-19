package com.konselyavisa.payment.cinetpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CinetPayAmountTest {

    @Test
    void convertsEuroCentsAndWestAfricanFrancs() {
        assertThat(CinetPayAmount.toMajorUnits("EUR", 8500)).isEqualTo(85);
        assertThat(CinetPayAmount.toMajorUnits("XOF", 5000)).isEqualTo(5000);
    }

    @Test
    void rejectsFractionalEuro() {
        assertThatThrownBy(() -> CinetPayAmount.toMajorUnits("EUR", 8501))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.cinetpay_amount_invalid");
    }
}
