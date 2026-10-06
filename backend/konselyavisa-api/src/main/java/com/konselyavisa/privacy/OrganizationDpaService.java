package com.konselyavisa.privacy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.identity.CurrentUser;
import com.konselyavisa.privacy.api.AcceptOrganizationDpaRequest;
import com.konselyavisa.privacy.api.OrganizationDpaMapper;
import com.konselyavisa.privacy.api.OrganizationDpaResponse;
import com.konselyavisa.privacy.domain.OrganizationDpaAgreement;
import com.konselyavisa.privacy.domain.OrganizationDpaStatus;
import com.konselyavisa.privacy.persistence.OrganizationDpaAgreementRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationDpaService {

    private final OrganizationDpaAgreementRepository repository;
    private final OrganizationDpaMapper mapper;

    public OrganizationDpaService(OrganizationDpaAgreementRepository repository, OrganizationDpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationDpaResponse> findActiveForCurrentOrg() {
        return repository
                .findByOrganizationIdAndStatus(requireOrganization(), OrganizationDpaStatus.ACTIVE)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationDpaAgreement> findActiveEntity(UUID organizationId) {
        return repository.findByOrganizationIdAndStatus(organizationId, OrganizationDpaStatus.ACTIVE);
    }

    @Transactional
    public OrganizationDpaResponse accept(AcceptOrganizationDpaRequest request) {
        UUID organizationId = requireOrganization();
        if (!OrganizationDpaCatalog.CURRENT_VERSION.equals(request.version())) {
            throw BusinessException.badRequest("error.privacy.dpa_version_stale");
        }
        repository
                .findByOrganizationIdAndStatus(organizationId, OrganizationDpaStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(OrganizationDpaStatus.SUPERSEDED);
                    repository.saveAndFlush(existing);
                });
        OrganizationDpaAgreement agreement = new OrganizationDpaAgreement();
        agreement.setOrganizationId(organizationId);
        agreement.setVersion(OrganizationDpaCatalog.CURRENT_VERSION);
        agreement.setStatus(OrganizationDpaStatus.ACTIVE);
        agreement.setRetentionDays(OrganizationDpaCatalog.DEFAULT_RETENTION_DAYS);
        agreement.setProcessorLegalName(OrganizationDpaCatalog.PROCESSOR_LEGAL_NAME);
        agreement.setControllerNameI18n(controllerNames(request.controllerNameI18n()));
        agreement.setSummaryI18n(new HashMap<>(OrganizationDpaCatalog.SUMMARY_I18N));
        agreement.setDocumentUri(blankToNull(request.documentUri()));
        agreement.setAcceptedAt(Instant.now());
        agreement.setAcceptedByLabel(label());
        return mapper.toResponse(repository.saveAndFlush(agreement));
    }

    private static String label() {
        String username = CurrentUser.username();
        if (username != null && !username.isBlank()) {
            return username;
        }
        String subject = CurrentUser.subject();
        return subject == null || subject.isBlank() ? "unknown" : subject;
    }

    private static Map<String, String> controllerNames(Map<String, String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw BusinessException.badRequest("error.privacy.dpa_controller_required");
        }
        Map<String, String> names = new HashMap<>();
        requested.forEach((locale, value) -> {
            if (locale != null && value != null && !value.isBlank()) {
                names.put(locale.trim(), value.trim());
            }
        });
        if (names.isEmpty() || !names.containsKey("fr")) {
            throw BusinessException.badRequest("error.privacy.dpa_controller_required");
        }
        return names;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID requireOrganization() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        return organizationId;
    }
}
