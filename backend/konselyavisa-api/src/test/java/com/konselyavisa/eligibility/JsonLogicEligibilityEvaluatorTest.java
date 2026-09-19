package com.konselyavisa.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
