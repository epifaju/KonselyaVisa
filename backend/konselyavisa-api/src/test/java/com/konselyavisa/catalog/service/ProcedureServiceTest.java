package com.konselyavisa.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProcedureServiceTest {

    @Mock
    private ProcedureDefinitionRepository procedureDefinitionRepository;

    @Mock
    private ProcedureVersionRepository procedureVersionRepository;

    @Mock
    private CountryService countryService;

    @Mock
    private ProcedureMapper procedureMapper;

    @Mock
    private ProcedureVersionMapper procedureVersionMapper;

    @Mock
    private JsonLogicEligibilityEvaluator eligibilityEvaluator;

    @Mock
    private ProcedureAccessGuard procedureAccessGuard;

    private ProcedureService service;
    private UUID procedureId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        procedureId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        service = new ProcedureService(
                procedureDefinitionRepository,
                procedureVersionRepository,
                countryService,
                procedureMapper,
                procedureVersionMapper,
                eligibilityEvaluator,
                procedureAccessGuard);
    }

    @Test
    void publishRejectsNonDraftVersion() {
        ProcedureVersion version = draftVersion();
        version.setStatus(ProcedureVersionStatus.PUBLISHED);
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.publish(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.version_immutable");
        verify(eligibilityEvaluator, never()).validate(any());
    }

    @Test
    void publishRejectsRequirementWithoutCode() {
        ProcedureVersion version = draftVersion();
        version.setDocumentRequirements(List.of(Map.of("labelI18n", Map.of("fr", "Passeport"))));
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.publish(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.requirement_code_required");
    }

    @Test
    void publishPropagatesInvalidEligibilityRules() {
        ProcedureVersion version = draftVersion();
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(version));
        doThrow(BusinessException.badRequest("error.eligibility.invalid_rules"))
                .when(eligibilityEvaluator)
                .validate(version.getEligibilityRules());

        assertThatThrownBy(() -> service.publish(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.invalid_rules");
    }

    @Test
    void publishArchivesPreviousPublishedVersion() {
        ProcedureVersion draft = draftVersion();
        draft.setDocumentRequirements(List.of(Map.of(
                "code", "PASSPORT",
                "labelI18n", Map.of("fr", "Passeport"))));
        ProcedureVersion previous = new ProcedureVersion();
        previous.setId(UUID.randomUUID());
        previous.setStatus(ProcedureVersionStatus.PUBLISHED);
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(draft));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        procedureId, ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(previous));
        ProcedureVersionResponse mapped = org.mockito.Mockito.mock(ProcedureVersionResponse.class);
        when(procedureVersionMapper.toResponse(draft)).thenReturn(mapped);

        ProcedureVersionResponse response = service.publish(procedureId, versionId);

        assertThat(response).isSameAs(mapped);
        assertThat(previous.getStatus()).isEqualTo(ProcedureVersionStatus.ARCHIVED);
        assertThat(draft.getStatus()).isEqualTo(ProcedureVersionStatus.PUBLISHED);
        assertThat(draft.getPublishedAt()).isNotNull();
    }

    @Test
    void createRejectsDuplicateCode() {
        CreateProcedureRequest request = createRequest("VISA_TOURISM", Map.of("fr", "Visa tourisme"));
        when(procedureDefinitionRepository.existsByCodeIgnoreCase("VISA_TOURISM")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_code_taken");
        verify(procedureDefinitionRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingDefaultLocale() {
        CreateProcedureRequest request = createRequest("VISA_TOURISM", Map.of("en", "Tourist visa"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.i18n_required");
        verify(procedureDefinitionRepository, never()).existsByCodeIgnoreCase(any());
    }

    @Test
    void createPersistsDefinitionAndDraftVersion() {
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Country origin = country("GW");
        Country destination = country("FR");
        CreateProcedureRequest request = new CreateProcedureRequest(
                " visa_tourism ",
                originId,
                destinationId,
                ProcedureCategory.VISA,
                Map.of("fr", "Visa tourisme"),
                Map.of("fr", "Desc"),
                Map.of("==", List.of(1, 1)),
                List.of(Map.of("code", "PASSPORT", "labelI18n", Map.of("fr", "Passeport"))));
        when(procedureDefinitionRepository.existsByCodeIgnoreCase(" visa_tourism ")).thenReturn(false);
        when(countryService.getCountry(originId)).thenReturn(origin);
        when(countryService.getCountry(destinationId)).thenReturn(destination);
        when(procedureDefinitionRepository.save(any(ProcedureDefinition.class))).thenAnswer(inv -> {
            ProcedureDefinition definition = inv.getArgument(0);
            definition.setId(procedureId);
            return definition;
        });
        when(procedureVersionRepository.save(any(ProcedureVersion.class))).thenAnswer(inv -> {
            ProcedureVersion version = inv.getArgument(0);
            version.setId(versionId);
            return version;
        });
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(List.of());
        ProcedureResponse mapped = org.mockito.Mockito.mock(ProcedureResponse.class);
        when(procedureMapper.toResponse(any(ProcedureDefinition.class), eq(null), eq(List.of())))
                .thenReturn(mapped);

        ProcedureResponse response = service.create(request);

        assertThat(response).isSameAs(mapped);
        ArgumentCaptor<ProcedureDefinition> definitionCaptor = ArgumentCaptor.forClass(ProcedureDefinition.class);
        verify(procedureDefinitionRepository).save(definitionCaptor.capture());
        assertThat(definitionCaptor.getValue().getCode()).isEqualTo("VISA_TOURISM");
        assertThat(definitionCaptor.getValue().isActive()).isTrue();
        assertThat(definitionCaptor.getValue().getOriginCountry()).isSameAs(origin);
        assertThat(definitionCaptor.getValue().getDestinationCountry()).isSameAs(destination);

        ArgumentCaptor<ProcedureVersion> versionCaptor = ArgumentCaptor.forClass(ProcedureVersion.class);
        verify(procedureVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo(ProcedureVersionStatus.DRAFT);
        assertThat(versionCaptor.getValue().getDocumentRequirements()).hasSize(1);
    }

    @Test
    void updatePatchesMutableFields() {
        ProcedureDefinition definition = definition();
        definition.setActive(true);
        definition.setCategory(ProcedureCategory.VISA);
        definition.setNameI18n(Map.of("fr", "Ancien"));
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(List.of());
        ProcedureResponse mapped = org.mockito.Mockito.mock(ProcedureResponse.class);
        when(procedureMapper.toResponse(definition, null, List.of())).thenReturn(mapped);

        ProcedureResponse response = service.update(
                procedureId,
                new UpdateProcedureRequest(
                        null,
                        null,
                        ProcedureCategory.EVISA,
                        Map.of("fr", "Nouveau"),
                        Map.of("fr", "Desc"),
                        false));

        assertThat(response).isSameAs(mapped);
        assertThat(definition.getCategory()).isEqualTo(ProcedureCategory.EVISA);
        assertThat(definition.getNameI18n()).containsEntry("fr", "Nouveau");
        assertThat(definition.getDescriptionI18n()).containsEntry("fr", "Desc");
        assertThat(definition.isActive()).isFalse();
    }

    @Test
    void updateRejectsMissingProcedure() {
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        procedureId, new UpdateProcedureRequest(null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    @Test
    void updateRejectsNameWithoutDefaultLocale() {
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition()));

        assertThatThrownBy(() -> service.update(
                        procedureId,
                        new UpdateProcedureRequest(null, null, null, Map.of("en", "Name"), null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.i18n_required");
    }

    @Test
    void updateVersionMutatesDraftFields() {
        ProcedureVersion version = draftVersion();
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(version));
        ProcedureVersionResponse mapped = org.mockito.Mockito.mock(ProcedureVersionResponse.class);
        when(procedureVersionMapper.toResponse(version)).thenReturn(mapped);

        ProcedureVersionResponse response = service.updateVersion(
                procedureId,
                versionId,
                new UpdateProcedureVersionRequest(
                        Map.of("!", true),
                        List.of(Map.of("code", "ID", "labelI18n", Map.of("fr", "ID"))),
                        21));

        assertThat(response).isSameAs(mapped);
        assertThat(version.getEstimatedInstructionDays()).isEqualTo(21);
        assertThat(version.getEligibilityRules()).containsKey("!");
        assertThat(version.getDocumentRequirements()).hasSize(1);
    }

    @Test
    void updateVersionRejectsPublishedAndInvalidDays() {
        ProcedureVersion published = draftVersion();
        published.setStatus(ProcedureVersionStatus.PUBLISHED);
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.updateVersion(
                        procedureId, versionId, new UpdateProcedureVersionRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.version_immutable");

        ProcedureVersion draft = draftVersion();
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.updateVersion(
                        procedureId, versionId, new UpdateProcedureVersionRequest(null, null, 400)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.estimated_days_invalid");
    }

    @Test
    void createVersionIncrementsNumberFromLatest() {
        ProcedureDefinition definition = definition();
        ProcedureVersion latest = draftVersion();
        latest.setVersionNumber(3);
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findTopByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(Optional.of(latest));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        procedureId, ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(procedureVersionRepository.save(any(ProcedureVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        ProcedureVersionResponse mapped = org.mockito.Mockito.mock(ProcedureVersionResponse.class);
        when(procedureVersionMapper.toResponse(any(ProcedureVersion.class))).thenReturn(mapped);

        service.createVersion(
                procedureId, new CreateProcedureVersionRequest(null, Map.of("==", 1), List.of(), 5));

        ArgumentCaptor<ProcedureVersion> versionCaptor = ArgumentCaptor.forClass(ProcedureVersion.class);
        verify(procedureVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(4);
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo(ProcedureVersionStatus.DRAFT);
        assertThat(versionCaptor.getValue().getEstimatedInstructionDays()).isEqualTo(5);
    }

    @Test
    void createVersionCopiesPublishedSourceWhenRequestOmitsRules() {
        ProcedureDefinition definition = definition();
        ProcedureVersion published = draftVersion();
        published.setStatus(ProcedureVersionStatus.PUBLISHED);
        published.setEligibilityRules(Map.of("copied", true));
        published.setDocumentRequirements(List.of(Map.of("code", "ID")));
        published.setPricing(Map.of("currency", "EUR"));
        published.setEstimatedInstructionDays(12);
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        procedureId, ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(published));
        when(procedureVersionRepository.findTopByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(Optional.of(published));
        when(procedureVersionRepository.save(any(ProcedureVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(procedureVersionMapper.toResponse(any(ProcedureVersion.class)))
                .thenReturn(org.mockito.Mockito.mock(ProcedureVersionResponse.class));

        service.createVersion(procedureId, new CreateProcedureVersionRequest(null, null, null, null));

        ArgumentCaptor<ProcedureVersion> versionCaptor = ArgumentCaptor.forClass(ProcedureVersion.class);
        verify(procedureVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getEligibilityRules()).containsEntry("copied", true);
        assertThat(versionCaptor.getValue().getDocumentRequirements()).hasSize(1);
        assertThat(versionCaptor.getValue().getPricing()).containsEntry("currency", "EUR");
        assertThat(versionCaptor.getValue().getEstimatedInstructionDays()).isEqualTo(12);
    }

    @Test
    void updateMovesCountriesAndRejectsZeroDays() {
        ProcedureDefinition definition = definition();
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Country origin = country("SN");
        Country destination = country("PT");
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));
        when(countryService.getCountry(originId)).thenReturn(origin);
        when(countryService.getCountry(destinationId)).thenReturn(destination);
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(List.of());
        when(procedureMapper.toResponse(definition, null, List.of()))
                .thenReturn(org.mockito.Mockito.mock(ProcedureResponse.class));

        service.update(
                procedureId,
                new UpdateProcedureRequest(originId, destinationId, null, null, null, null));

        assertThat(definition.getOriginCountry()).isSameAs(origin);
        assertThat(definition.getDestinationCountry()).isSameAs(destination);

        ProcedureVersion draft = draftVersion();
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.updateVersion(
                        procedureId, versionId, new UpdateProcedureVersionRequest(null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.estimated_days_invalid");
    }

    @Test
    void publishRejectsBlankCodeAndNonMapLabels() {
        ProcedureVersion blankCode = draftVersion();
        blankCode.setDocumentRequirements(List.of(Map.of("code", "  ", "labelI18n", Map.of("fr", "Pièce"))));
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(blankCode));
        assertThatThrownBy(() -> service.publish(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.requirement_code_required");

        ProcedureVersion badLabels = draftVersion();
        badLabels.setDocumentRequirements(List.of(Map.of("code", "ID", "labelI18n", "Passeport")));
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(badLabels));
        assertThatThrownBy(() -> service.publish(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.i18n_required");

        ProcedureVersion partialLabels = draftVersion();
        Map<Object, Object> labels = new java.util.LinkedHashMap<>();
        labels.put("fr", 1);
        labels.put(1, "ignored");
        Map<String, Object> requirement = new java.util.LinkedHashMap<>();
        requirement.put("code", "ID");
        requirement.put("labelI18n", labels);
        partialLabels.setDocumentRequirements(List.of(requirement));
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(partialLabels));
        assertThatThrownBy(() -> service.publish(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.i18n_required");
    }

    @Test
    void archiveMarksVersionArchived() {
        ProcedureVersion version = draftVersion();
        version.setStatus(ProcedureVersionStatus.PUBLISHED);
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(version));
        ProcedureVersionResponse mapped = org.mockito.Mockito.mock(ProcedureVersionResponse.class);
        when(procedureVersionMapper.toResponse(version)).thenReturn(mapped);

        ProcedureVersionResponse response = service.archive(procedureId, versionId);

        assertThat(response).isSameAs(mapped);
        assertThat(version.getStatus()).isEqualTo(ProcedureVersionStatus.ARCHIVED);
    }

    @Test
    void archiveIsIdempotentWhenAlreadyArchived() {
        ProcedureVersion version = draftVersion();
        version.setStatus(ProcedureVersionStatus.ARCHIVED);
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.of(version));
        ProcedureVersionResponse mapped = org.mockito.Mockito.mock(ProcedureVersionResponse.class);
        when(procedureVersionMapper.toResponse(version)).thenReturn(mapped);

        assertThat(service.archive(procedureId, versionId)).isSameAs(mapped);
        assertThat(version.getStatus()).isEqualTo(ProcedureVersionStatus.ARCHIVED);
    }

    @Test
    void archiveRejectsUnknownVersion() {
        when(procedureVersionRepository.findByIdAndProcedureDefinitionId(versionId, procedureId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(procedureId, versionId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.version_not_found");
    }

    @Test
    void searchWithDraftsMapsAllDefinitions() {
        Pageable pageable = PageRequest.of(0, 10);
        ProcedureDefinition definition = definition();
        when(procedureDefinitionRepository.search(null, null, null, false, false, pageable))
                .thenReturn(new PageImpl<>(List.of(definition), pageable, 1));
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(List.of());
        ProcedureResponse mapped = org.mockito.Mockito.mock(ProcedureResponse.class);
        when(procedureMapper.toResponse(definition, null, List.of())).thenReturn(mapped);

        PageResponse<ProcedureResponse> page = service.search(null, null, null, true, pageable);

        assertThat(page.content()).containsExactly(mapped);
        verify(procedureAccessGuard, never()).isOfferedToCitizens(any());
    }

    @Test
    void searchForCitizensFiltersUnofferedProcedures() {
        Pageable pageable = PageRequest.of(0, 10);
        ProcedureDefinition offered = definition();
        ProcedureDefinition hidden = definition();
        hidden.setId(UUID.randomUUID());
        when(procedureDefinitionRepository.search(null, null, ProcedureCategory.VISA, true, true, pageable))
                .thenReturn(new PageImpl<>(List.of(offered, hidden), pageable, 2));
        when(procedureAccessGuard.isOfferedToCitizens(offered)).thenReturn(true);
        when(procedureAccessGuard.isOfferedToCitizens(hidden)).thenReturn(false);
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(offered.getId()))
                .thenReturn(List.of());
        ProcedureResponse mapped = org.mockito.Mockito.mock(ProcedureResponse.class);
        when(procedureMapper.toResponse(offered, null, List.of())).thenReturn(mapped);

        PageResponse<ProcedureResponse> page =
                service.search(null, null, ProcedureCategory.VISA, false, pageable);

        assertThat(page.content()).containsExactly(mapped);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void getByIdRejectsUnknownProcedure() {
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(procedureId, true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    @Test
    void getByIdRejectsInactiveProcedureForCitizens() {
        ProcedureDefinition definition = definition();
        definition.setActive(false);
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.getById(procedureId, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    @Test
    void getByIdRejectsProcedureWithoutPublishedVersionForCitizens() {
        ProcedureDefinition definition = definition();
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(List.of(draftVersion()));
        ProcedureResponse mapped = new ProcedureResponse(
                procedureId,
                "VISA_TOURISM",
                null,
                null,
                ProcedureCategory.VISA,
                Map.of("fr", "Visa"),
                Map.of(),
                true,
                null,
                List.of());
        when(procedureMapper.toResponse(eq(definition), eq(null), any())).thenReturn(mapped);

        assertThatThrownBy(() -> service.getById(procedureId, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
        verify(procedureAccessGuard).assertOfferedToCitizens(definition);
    }

    @Test
    void getByIdReturnsPublishedProcedureForCitizens() {
        ProcedureDefinition definition = definition();
        ProcedureVersion published = draftVersion();
        published.setStatus(ProcedureVersionStatus.PUBLISHED);
        when(procedureDefinitionRepository.findDetailedById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdOrderByVersionNumberDesc(procedureId))
                .thenReturn(List.of(published));
        ProcedureResponse mapped = new ProcedureResponse(
                procedureId,
                "VISA_TOURISM",
                null,
                null,
                ProcedureCategory.VISA,
                Map.of("fr", "Visa"),
                Map.of(),
                true,
                2,
                List.of());
        when(procedureMapper.toResponse(definition, 2, List.of(published))).thenReturn(mapped);

        assertThat(service.getById(procedureId, false)).isSameAs(mapped);
        verify(procedureAccessGuard).assertOfferedToCitizens(definition);
    }

    private CreateProcedureRequest createRequest(String code, Map<String, String> nameI18n) {
        return new CreateProcedureRequest(
                code,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ProcedureCategory.VISA,
                nameI18n,
                Map.of(),
                Map.of(),
                List.of());
    }

    private ProcedureDefinition definition() {
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setId(procedureId);
        definition.setCode("VISA_TOURISM");
        definition.setActive(true);
        return definition;
    }

    private ProcedureVersion draftVersion() {
        ProcedureVersion version = new ProcedureVersion();
        version.setId(versionId);
        version.setProcedureDefinition(definition());
        version.setStatus(ProcedureVersionStatus.DRAFT);
        version.setVersionNumber(2);
        version.setEligibilityRules(Map.of("==", List.of(1, 1)));
        version.setDocumentRequirements(List.of());
        version.setEstimatedInstructionDays(10);
        return version;
    }

    private static Country country(String iso) {
        Country country = new Country();
        country.setId(UUID.randomUUID());
        country.setIsoCode(iso);
        return country;
    }
}
