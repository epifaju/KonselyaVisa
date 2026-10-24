package com.konselyavisa.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcedureAccessGuardTest {

    @Mock
    private FeatureFlagService featureFlagService;

    private ProcedureAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProcedureAccessGuard(featureFlagService);
    }

    @Test
    void isOfferedToCitizensDelegatesToFeatureFlag() {
        ProcedureDefinition definition = procedure("VISA_TOURISM");
        when(featureFlagService.isProcedureOffered("VISA_TOURISM")).thenReturn(true);

        assertThat(guard.isOfferedToCitizens(definition)).isTrue();
        verify(featureFlagService).isProcedureOffered("VISA_TOURISM");
    }

    @Test
    void assertOfferedToCitizensPassesWhenOffered() {
        when(featureFlagService.isProcedureOffered("VISA_TOURISM")).thenReturn(true);

        guard.assertOfferedToCitizens(procedure("VISA_TOURISM"));
    }

    @Test
    void assertOfferedToCitizensThrowsWhenNotOffered() {
        when(featureFlagService.isProcedureOffered("HIDDEN")).thenReturn(false);

        assertThatThrownBy(() -> guard.assertOfferedToCitizens(procedure("HIDDEN")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    private static ProcedureDefinition procedure(String code) {
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setCode(code);
        return definition;
    }
}
