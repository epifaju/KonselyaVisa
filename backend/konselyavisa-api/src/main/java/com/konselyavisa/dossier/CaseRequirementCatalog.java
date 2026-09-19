package com.konselyavisa.dossier;

import com.konselyavisa.catalog.domain.ProcedureVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CaseRequirementCatalog {

    private CaseRequirementCatalog() {}

    public static List<String> requiredCodes(ProcedureVersion version) {
        List<String> codes = new ArrayList<>();
        if (version == null || version.getDocumentRequirements() == null) {
            return codes;
        }
        for (Map<String, Object> requirement : version.getDocumentRequirements()) {
            if (requirement == null) {
                continue;
            }
            Object code = requirement.get("code");
            if (!(code instanceof String text) || text.isBlank() || !isRequired(requirement.get("required"))) {
                continue;
            }
            codes.add(text);
        }
        return codes;
    }

    private static boolean isRequired(Object required) {
        if (required == null) {
            return true;
        }
        if (required instanceof Boolean flag) {
            return flag;
        }
        return !"false".equalsIgnoreCase(String.valueOf(required));
    }
}
