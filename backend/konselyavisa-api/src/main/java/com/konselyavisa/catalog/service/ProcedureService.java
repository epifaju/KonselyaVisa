package com.konselyavisa.catalog.service;

import com.konselyavisa.catalog.api.CreateProcedureRequest;
import com.konselyavisa.catalog.api.CreateProcedureVersionRequest;
import com.konselyavisa.catalog.api.ProcedureMapper;
import com.konselyavisa.catalog.api.ProcedureResponse;
import com.konselyavisa.catalog.api.ProcedureVersionMapper;
import com.konselyavisa.catalog.api.ProcedureVersionResponse;
import com.konselyavisa.catalog.api.UpdateProcedureRequest;
import com.konselyavisa.catalog.api.UpdateProcedureVersionRequest;
import com.konselyavisa.catalog.domain.Country;
import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.persistence.ProcedureVersionRepository;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.common.i18n.LocalizedText;
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcedureService {

    private final ProcedureDefinitionRepository procedureDefinitionRepository;
    private final ProcedureVersionRepository procedureVersionRepository;
    private final CountryService countryService;
    private final ProcedureMapper procedureMapper;
    private final ProcedureVersionMapper procedureVersionMapper;
    private final JsonLogicEligibilityEvaluator eligibilityEvaluator;
    private final ProcedureAccessGuard procedureAccessGuard;

    public ProcedureService(
            ProcedureDefinitionRepository procedureDefinitionRepository,
            ProcedureVersionRepository procedureVersionRepository,
            CountryService countryService,
            ProcedureMapper procedureMapper,
            ProcedureVersionMapper procedureVersionMapper,
            JsonLogicEligibilityEvaluator eligibilityEvaluator,
            ProcedureAccessGuard procedureAccessGuard) {
        this.procedureDefinitionRepository = procedureDefinitionRepository;
        this.procedureVersionRepository = procedureVersionRepository;
        this.countryService = countryService;
        this.procedureMapper = procedureMapper;
        this.procedureVersionMapper = procedureVersionMapper;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.procedureAccessGuard = procedureAccessGuard;
    }

    @Cacheable(
            cacheNames = "catalog-procedures",
            key =
                    "T(java.util.Objects).toString(T(com.konselyavisa.tenancy.TenantContext).getOrganizationId()) + ':' + #originCountryId + ':' + #destinationCountryId + ':' + #category + ':' + #includeDrafts + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public PageResponse<ProcedureResponse> search(
            UUID originCountryId,
            UUID destinationCountryId,
            ProcedureCategory category,
            boolean includeDrafts,
            Pageable pageable) {
        boolean publishedOnly = !includeDrafts;
        boolean activeOnly = !includeDrafts;
        Page<ProcedureDefinition> page = procedureDefinitionRepository.search(
                originCountryId, destinationCountryId, category, activeOnly, publishedOnly, pageable);
        if (includeDrafts) {
            return PageResponse.from(page.map(definition -> toResponse(definition, true)));
        }
        List<ProcedureDefinition> offered = page.getContent().stream()
                .filter(procedureAccessGuard::isOfferedToCitizens)
                .toList();
        long total = page.getTotalElements() - (page.getNumberOfElements() - offered.size());
        Page<ProcedureResponse> mapped = new PageImpl<>(
                offered.stream().map(definition -> toResponse(definition, false)).toList(),
                pageable,
                Math.max(0, total));
        return PageResponse.from(mapped);
    }

    @Cacheable(
            cacheNames = "catalog-procedure",
            key =
                    "T(java.util.Objects).toString(T(com.konselyavisa.tenancy.TenantContext).getOrganizationId()) + ':' + #id + ':' + #includeDrafts")
    @Transactional(readOnly = true)
    public ProcedureResponse getById(UUID id, boolean includeDrafts) {
        ProcedureDefinition definition = procedureDefinitionRepository
                .findDetailedById(id)
                .orElseThrow(() -> BusinessException.notFound("error.catalog.procedure_not_found"));
        if (!includeDrafts && !definition.isActive()) {
            throw BusinessException.notFound("error.catalog.procedure_not_found");
        }
        if (!includeDrafts) {
            procedureAccessGuard.assertOfferedToCitizens(definition);
        }
        ProcedureResponse response = toResponse(definition, includeDrafts);
        if (!includeDrafts && response.publishedVersionNumber() == null) {
            throw BusinessException.notFound("error.catalog.procedure_not_found");
        }
        return response;
    }

    @CacheEvict(
            cacheNames = {"catalog-procedures", "catalog-procedure"},
            allEntries = true)
    @Transactional
    public ProcedureResponse create(CreateProcedureRequest request) {
        LocalizedText.requireDefaultLocale(request.nameI18n(), "error.catalog.i18n_required");
        if (procedureDefinitionRepository.existsByCodeIgnoreCase(request.code())) {
            throw BusinessException.conflict("error.catalog.procedure_code_taken");
        }
        Country origin = countryService.getCountry(request.originCountryId());
        Country destination = countryService.getCountry(request.destinationCountryId());
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setCode(request.code().trim().toUpperCase());
        definition.setOriginCountry(origin);
        definition.setDestinationCountry(destination);
        definition.setCategory(request.category());
        definition.setNameI18n(new HashMap<>(request.nameI18n()));
        definition.setDescriptionI18n(
                request.descriptionI18n() == null ? new HashMap<>() : new HashMap<>(request.descriptionI18n()));
        definition.setActive(true);
        procedureDefinitionRepository.save(definition);

        ProcedureVersion version = new ProcedureVersion();
        version.setProcedureDefinition(definition);
        version.setVersionNumber(1);
        version.setStatus(ProcedureVersionStatus.DRAFT);
        version.setEligibilityRules(copyRules(request.eligibilityRules()));
        version.setDocumentRequirements(copyRequirements(request.documentRequirements()));
        procedureVersionRepository.save(version);
        return toResponse(definition, true);
    }

    @CacheEvict(
            cacheNames = {"catalog-procedures", "catalog-procedure"},
            allEntries = true)
    @Transactional
    public ProcedureResponse update(UUID id, UpdateProcedureRequest request) {
        ProcedureDefinition definition = getDefinition(id);
        if (request.originCountryId() != null) {
            definition.setOriginCountry(countryService.getCountry(request.originCountryId()));
        }
        if (request.destinationCountryId() != null) {
            definition.setDestinationCountry(countryService.getCountry(request.destinationCountryId()));
        }
        if (request.category() != null) {
            definition.setCategory(request.category());
        }
        if (request.nameI18n() != null) {
            LocalizedText.requireDefaultLocale(request.nameI18n(), "error.catalog.i18n_required");
            definition.setNameI18n(new HashMap<>(request.nameI18n()));
        }
        if (request.descriptionI18n() != null) {
            definition.setDescriptionI18n(new HashMap<>(request.descriptionI18n()));
        }
        if (request.active() != null) {
            definition.setActive(request.active());
        }
        return toResponse(definition, true);
    }

    @CacheEvict(
            cacheNames = {"catalog-procedures", "catalog-procedure"},
            allEntries = true)
    @Transactional
    public ProcedureVersionResponse createVersion(UUID procedureId, CreateProcedureVersionRequest request) {
        ProcedureDefinition definition = getDefinition(procedureId);
        ProcedureVersion source = resolveCopySource(procedureId, request.copyFromVersionId());
        int nextNumber = procedureVersionRepository
                .findTopByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId)
                .map(ProcedureVersion::getVersionNumber)
                .orElse(0)
                + 1;
        ProcedureVersion version = new ProcedureVersion();
        version.setProcedureDefinition(definition);
        version.setVersionNumber(nextNumber);
        version.setStatus(ProcedureVersionStatus.DRAFT);
        if (request.eligibilityRules() != null) {
            version.setEligibilityRules(copyRules(request.eligibilityRules()));
        } else if (source != null) {
            version.setEligibilityRules(copyRules(source.getEligibilityRules()));
        }
        if (request.documentRequirements() != null) {
            version.setDocumentRequirements(copyRequirements(request.documentRequirements()));
        } else if (source != null) {
            version.setDocumentRequirements(copyRequirements(source.getDocumentRequirements()));
        }
        if (source != null) {
            version.setPricing(copyRules(source.getPricing()));
            version.setEstimatedInstructionDays(source.getEstimatedInstructionDays());
        }
        if (request.estimatedInstructionDays() != null) {
            validateEstimatedInstructionDays(request.estimatedInstructionDays());
            version.setEstimatedInstructionDays(request.estimatedInstructionDays());
        }
        return procedureVersionMapper.toResponse(procedureVersionRepository.save(version));
    }

    @CacheEvict(
            cacheNames = {"catalog-procedures", "catalog-procedure"},
            allEntries = true)
    @Transactional
    public ProcedureVersionResponse updateVersion(
            UUID procedureId, UUID versionId, UpdateProcedureVersionRequest request) {
        ProcedureVersion version = getVersion(procedureId, versionId);
        assertDraft(version);
        if (request.eligibilityRules() != null) {
            version.setEligibilityRules(copyRules(request.eligibilityRules()));
        }
        if (request.documentRequirements() != null) {
            version.setDocumentRequirements(copyRequirements(request.documentRequirements()));
        }
        if (request.estimatedInstructionDays() != null) {
            validateEstimatedInstructionDays(request.estimatedInstructionDays());
            version.setEstimatedInstructionDays(request.estimatedInstructionDays());
        }
        return procedureVersionMapper.toResponse(version);
    }

    @CacheEvict(
            cacheNames = {"catalog-procedures", "catalog-procedure"},
            allEntries = true)
    @Transactional
    public ProcedureVersionResponse publish(UUID procedureId, UUID versionId) {
        ProcedureVersion version = getVersion(procedureId, versionId);
        assertDraft(version);
        eligibilityEvaluator.validate(version.getEligibilityRules());
        validateDocumentRequirements(version.getDocumentRequirements());
        validateEstimatedInstructionDays(version.getEstimatedInstructionDays());
        procedureVersionRepository
                .findByProcedureDefinitionIdAndStatus(procedureId, ProcedureVersionStatus.PUBLISHED)
                .ifPresent(current -> {
                    current.setStatus(ProcedureVersionStatus.ARCHIVED);
                    current.setPublishedAt(current.getPublishedAt());
                });
        version.setStatus(ProcedureVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        return procedureVersionMapper.toResponse(version);
    }

    @CacheEvict(
            cacheNames = {"catalog-procedures", "catalog-procedure"},
            allEntries = true)
    @Transactional
    public ProcedureVersionResponse archive(UUID procedureId, UUID versionId) {
        ProcedureVersion version = getVersion(procedureId, versionId);
        if (version.getStatus() == ProcedureVersionStatus.ARCHIVED) {
            return procedureVersionMapper.toResponse(version);
        }
        version.setStatus(ProcedureVersionStatus.ARCHIVED);
        return procedureVersionMapper.toResponse(version);
    }

    private ProcedureResponse toResponse(ProcedureDefinition definition, boolean includeDrafts) {
        List<ProcedureVersion> versions =
                procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(definition.getId());
        Integer publishedNumber = versions.stream()
                .filter(version -> version.getStatus() == ProcedureVersionStatus.PUBLISHED)
                .map(ProcedureVersion::getVersionNumber)
                .findFirst()
                .orElse(null);
        List<ProcedureVersion> visible = includeDrafts
                ? versions
                : versions.stream()
                        .filter(version -> version.getStatus() == ProcedureVersionStatus.PUBLISHED)
                        .toList();
        return procedureMapper.toResponse(definition, publishedNumber, visible);
    }

    private ProcedureDefinition getDefinition(UUID id) {
        return procedureDefinitionRepository
                .findDetailedById(id)
                .orElseThrow(() -> BusinessException.notFound("error.catalog.procedure_not_found"));
    }

    private ProcedureVersion getVersion(UUID procedureId, UUID versionId) {
        return procedureVersionRepository
                .findByIdAndProcedureDefinitionId(versionId, procedureId)
                .orElseThrow(() -> BusinessException.notFound("error.catalog.version_not_found"));
    }

    private ProcedureVersion resolveCopySource(UUID procedureId, UUID copyFromVersionId) {
        if (copyFromVersionId != null) {
            return getVersion(procedureId, copyFromVersionId);
        }
        return procedureVersionRepository
                .findByProcedureDefinitionIdAndStatus(procedureId, ProcedureVersionStatus.PUBLISHED)
                .or(() -> procedureVersionRepository.findTopByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .orElse(null);
    }

    private static void assertDraft(ProcedureVersion version) {
        if (version.getStatus() != ProcedureVersionStatus.DRAFT) {
            throw BusinessException.conflict("error.catalog.version_immutable");
        }
    }

    private static Map<String, Object> copyRules(Map<String, Object> rules) {
        return rules == null ? new HashMap<>() : new HashMap<>(rules);
    }

    private static List<Map<String, Object>> copyRequirements(List<Map<String, Object>> requirements) {
        if (requirements == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> requirement : requirements) {
            copy.add(new HashMap<>(requirement));
        }
        return copy;
    }

    private static void validateEstimatedInstructionDays(Integer days) {
        if (days == null) {
            return;
        }
        if (days < 1 || days > 365) {
            throw BusinessException.badRequest("error.catalog.estimated_days_invalid");
        }
    }

    private static void validateDocumentRequirements(List<Map<String, Object>> requirements) {
        if (requirements == null) {
            return;
        }
        for (Map<String, Object> requirement : requirements) {
            Object code = requirement.get("code");
            if (!(code instanceof String text) || text.isBlank()) {
                throw BusinessException.badRequest("error.catalog.requirement_code_required");
            }
            Object labels = requirement.get("labelI18n");
            if (!(labels instanceof Map<?, ?> labelMap)) {
                throw BusinessException.badRequest("error.catalog.i18n_required");
            }
            Map<String, String> i18n = new HashMap<>();
            labelMap.forEach((key, value) -> {
                if (key instanceof String locale && value instanceof String label) {
                    i18n.put(locale, label);
                }
            });
            LocalizedText.requireDefaultLocale(i18n, "error.catalog.i18n_required");
        }
    }
}
