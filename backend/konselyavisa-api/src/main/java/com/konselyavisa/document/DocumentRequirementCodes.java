package com.konselyavisa.document;

import com.konselyavisa.catalog.domain.ProcedureVersion;
import java.util.List;
import java.util.Map;

public final class DocumentRequirementCodes {

    private DocumentRequirementCodes() {}

    public static boolean isDeclared(ProcedureVersion version, String requirementCode) {
        if (version == null || requirementCode == null || requirementCode.isBlank()) {
            return false;
        }
        List<Map<String, Object>> requirements = version.getDocumentRequirements();
        if (requirements == null) {
            return false;
        }
        for (Map<String, Object> requirement : requirements) {
            if (requirement != null && requirementCode.equals(requirement.get("code"))) {
                return true;
            }
        }
        return false;
    }
}
