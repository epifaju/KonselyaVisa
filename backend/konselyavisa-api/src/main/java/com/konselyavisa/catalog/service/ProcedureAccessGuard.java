package com.konselyavisa.catalog.service;

import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.FeatureFlagService;
import org.springframework.stereotype.Component;

@Component
public class ProcedureAccessGuard {

    private final FeatureFlagService featureFlagService;

    public ProcedureAccessGuard(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    public boolean isOfferedToCitizens(ProcedureDefinition definition) {
        return featureFlagService.isProcedureOffered(definition.getCode());
    }

    public void assertOfferedToCitizens(ProcedureDefinition definition) {
        if (!isOfferedToCitizens(definition)) {
            throw BusinessException.notFound("error.catalog.procedure_not_found");
        }
    }
}
