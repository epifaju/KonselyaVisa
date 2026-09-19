package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicantFactsGuardTest {

    @Test
    void allowsEligibilityFacts() {
        Map<String, Object> facts = Map.of("nationality", "GW", "passportValidityMonths", 8);
        assertThat(ApplicantFactsGuard.sanitize(facts)).isEqualTo(facts);
    }

    @Test
    void rejectsPassportNumber() {
        assertThatThrownBy(() -> ApplicantFactsGuard.sanitize(Map.of("passportNumber", "XX123")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.sensitive_fact_forbidden");
    }
}
