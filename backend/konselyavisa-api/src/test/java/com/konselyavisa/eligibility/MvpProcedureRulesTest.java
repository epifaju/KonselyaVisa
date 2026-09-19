package com.konselyavisa.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MvpProcedureRulesTest {

    private JsonLogicEligibilityEvaluator evaluator;

    static final Map<String, Object> LEGALIZATION_FR_GW = Map.of(
            "and",
            List.of(
                    Map.of(
                            "in",
                            List.of(
                                    Map.of("var", "documentType"),
                                    List.of("BIRTH_CERTIFICATE", "DIPLOMA", "CRIMINAL_RECORD"))),
                    Map.of("in", List.of(Map.of("var", "nationality"), List.of("FR", "PT", "GW")))));

    static final Map<String, Object> APOSTILLE_FR_PT = Map.of(
            "and",
            List.of(
                    Map.of("==", List.of(Map.of("var", "hagueConvention"), true)),
                    Map.of("in", List.of(Map.of("var", "nationality"), List.of("FR", "PT")))));

    @BeforeEach
    void setUp() {
        evaluator = new JsonLogicEligibilityEvaluator(new ObjectMapper());
    }

    @Test
    void legalizationEligibleForFrenchBirthCertificate() {
        assertThat(evaluator.evaluate(
                        LEGALIZATION_FR_GW, Map.of("documentType", "BIRTH_CERTIFICATE", "nationality", "FR")))
                .isTrue();
    }

    @Test
    void legalizationIneligibleForUnsupportedDocument() {
        assertThat(evaluator.evaluate(
                        LEGALIZATION_FR_GW, Map.of("documentType", "PASSPORT", "nationality", "FR")))
                .isFalse();
    }

    @Test
    void legalizationIneligibleForUnknownNationality() {
        assertThat(evaluator.evaluate(
                        LEGALIZATION_FR_GW, Map.of("documentType", "DIPLOMA", "nationality", "SN")))
                .isFalse();
    }

    @Test
    void apostilleEligibleWhenHagueAndNationalityAllowed() {
        assertThat(evaluator.evaluate(
                        APOSTILLE_FR_PT, Map.of("hagueConvention", true, "nationality", "PT")))
                .isTrue();
    }

    @Test
    void apostilleIneligibleOutsideHague() {
        assertThat(evaluator.evaluate(
                        APOSTILLE_FR_PT, Map.of("hagueConvention", false, "nationality", "FR")))
                .isFalse();
    }

    @Test
    void apostilleIneligibleForGuineaBissauNationality() {
        assertThat(evaluator.evaluate(
                        APOSTILLE_FR_PT, Map.of("hagueConvention", true, "nationality", "GW")))
                .isFalse();
    }
}
