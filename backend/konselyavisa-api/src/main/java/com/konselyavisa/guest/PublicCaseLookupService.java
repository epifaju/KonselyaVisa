package com.konselyavisa.guest;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCaseLookupService {

    private static final List<String> DATE_OF_BIRTH_KEYS = List.of("dateOfBirth", "birthDate", "date_of_birth");

    private final GuestEligibilityService guestEligibilityService;
    private final CaseFileRepository caseFileRepository;

    public PublicCaseLookupService(
            GuestEligibilityService guestEligibilityService, CaseFileRepository caseFileRepository) {
        this.guestEligibilityService = guestEligibilityService;
        this.caseFileRepository = caseFileRepository;
    }

    public void bindBeforeLookup(UUID organizationId) {
        guestEligibilityService.bindOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    public PublicCaseStatusResponse lookup(PublicCaseLookupRequest request) {
        String email = normalize(request.email());
        String dateOfBirth = normalizeDob(request.dateOfBirth());
        if (email == null && dateOfBirth == null) {
            throw BusinessException.badRequest("error.guest.lookup_identity_required");
        }
        UUID organizationId = guestEligibilityService.bindOrganization(request.organizationId());
        CaseFile caseFile = caseFileRepository
                .findByOrganizationIdAndReferenceIgnoreCase(organizationId, request.reference().trim())
                .orElseThrow(PublicCaseLookupService::notFound);
        if (!identityMatches(caseFile, email, dateOfBirth)) {
            throw notFound();
        }
        return new PublicCaseStatusResponse(
                caseFile.getReference(),
                caseFile.getStatus(),
                caseFile.getProcedureDefinition().getNameI18n(),
                "case.status." + caseFile.getStatus().name());
    }

    private static boolean identityMatches(CaseFile caseFile, String email, String dateOfBirth) {
        if (email != null) {
            String stored = caseFile.getApplicant() == null ? null : normalize(caseFile.getApplicant().getEmail());
            return email.equals(stored);
        }
        Map<String, Object> facts = caseFile.getApplicantFacts();
        if (facts == null) {
            return false;
        }
        return dateOfBirth.equals(firstDob(facts));
    }

    private static String firstDob(Map<String, Object> facts) {
        for (String key : DATE_OF_BIRTH_KEYS) {
            Object value = facts.get(key);
            String normalized = normalizeDob(value == null ? null : value.toString());
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDob(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private static BusinessException notFound() {
        return BusinessException.notFound("error.guest.case_not_found");
    }
}
