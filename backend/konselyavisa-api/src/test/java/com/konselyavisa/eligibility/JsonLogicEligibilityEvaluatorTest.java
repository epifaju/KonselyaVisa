package com.konselyavisa.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonLogicEligibilityEvaluatorTest {

    private JsonLogicEligibilityEvaluator evaluator;

    private static final Map<String, Object> VISA_TOURISM_RULES = Map.of(
            "and",
            List.of(
                    Map.of(">=", List.of(Map.of("var", "passportValidityMonths"), 6)),
                    Map.of("in", List.of(Map.of("var", "nationality"), List.of("GW", "PT", "FR")))));

    @BeforeEach
    void setUp() {
        evaluator = new JsonLogicEligibilityEvaluator(new ObjectMapper());
    }

    @Test
    void eligibleWhenPassportValidAndNationalityAllowed() {
        assertThat(evaluator.evaluate(
                        VISA_TOURISM_RULES, Map.of("passportValidityMonths", 8, "nationality", "GW")))
                .isTrue();
    }

    @Test
    void ineligibleWhenPassportExpiresSoon() {
        assertThat(evaluator.evaluate(
                        VISA_TOURISM_RULES, Map.of("passportValidityMonths", 3, "nationality", "GW")))
                .isFalse();
    }

    @Test
    void ineligibleWhenNationalityNotInList() {
        assertThat(evaluator.evaluate(
                        VISA_TOURISM_RULES, Map.of("passportValidityMonths", 12, "nationality", "SN")))
                .isFalse();
    }

    @Test
    void emptyRulesAreAlwaysEligible() {
        assertThat(evaluator.evaluate(Map.of(), Map.of())).isTrue();
    }

    @Test
    void nullRulesAreAlwaysEligible() {
        assertThat(evaluator.evaluate(null, Map.of("nationality", "FR"))).isTrue();
    }

    @Test
    void missingFactIsNotEligible() {
        assertThat(evaluator.evaluate(VISA_TOURISM_RULES, Map.of("nationality", "FR"))).isFalse();
    }

    @Test
    void boundaryPassportValidityOfSixMonthsIsEligible() {
        assertThat(evaluator.evaluate(
                        VISA_TOURISM_RULES, Map.of("passportValidityMonths", 6, "nationality", "PT")))
                .isTrue();
    }

    @Test
    void blankNationalityIsNotEligible() {
        assertThat(evaluator.evaluate(
                        VISA_TOURISM_RULES, Map.of("passportValidityMonths", 12, "nationality", "")))
                .isFalse();
    }

    @Test
    void nonBooleanResultIsRejected() {
        assertThatThrownBy(() -> evaluator.evaluate(Map.of("var", "nationality"), Map.of("nationality", "FR")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.invalid_result");
    }

    @Test
    void invalidRulesAreRejected() {
        assertThatThrownBy(() -> evaluator.evaluate(Map.of("not-an-operator", List.of(1)), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.invalid_rules");
    }

    @Test
    void validateAcceptsEmptyRulesAndRejectsInvalidOnes() {
        evaluator.validate(Map.of());
        assertThatThrownBy(() -> evaluator.validate(Map.of("not-an-operator", List.of(1))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.invalid_rules");
    }
}
