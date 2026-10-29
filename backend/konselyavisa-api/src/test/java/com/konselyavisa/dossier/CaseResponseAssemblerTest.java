package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.appointment.persistence.CaseAppointmentRepository;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.api.CaseMapper;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.payment.persistence.CaseOrderRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseResponseAssemblerTest {

    @Mock
    private CaseMapper caseMapper;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    @Mock
    private CaseOrderRepository caseOrderRepository;

    @Mock
    private CaseAppointmentRepository caseAppointmentRepository;

    private CaseResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new CaseResponseAssembler(
                caseMapper, caseDocumentRepository, caseOrderRepository, caseAppointmentRepository);
    }

    @Test
    void toResponsesReturnsEmptyWithoutRepositoryCalls() {
        assertThat(assembler.toResponses(List.of())).isEmpty();
        verify(caseDocumentRepository, never()).findByCaseFile_IdIn(anyList());
        verify(caseOrderRepository, never()).findByCaseFile_IdIn(anyList());
        verify(caseAppointmentRepository, never()).findByCaseFile_IdIn(anyList());
    }

    @Test
    void toResponseAppliesProgressAndDuplicateFlag() {
        UUID orgId = UUID.randomUUID();
        CaseFile caseFile = caseFile(CaseStatus.COMPLETED);
        CaseDocument owned = document(orgId, "hash-dup", caseFile, applicant());
        CaseDocument other = document(orgId, "hash-dup", caseFile(CaseStatus.IN_PROGRESS), applicant());

        when(caseDocumentRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of(owned));
        when(caseOrderRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseAppointmentRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseDocumentRepository.findByOrganizationIdAndSha256In(orgId, List.of("hash-dup")))
                .thenReturn(List.of(owned, other));

        CaseResponse mapped = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse withProgress = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse flagged = org.mockito.Mockito.mock(CaseResponse.class);
        when(caseMapper.toResponse(caseFile)).thenReturn(mapped);
        when(mapped.withProgress(any(CaseNextActionDecision.class))).thenReturn(withProgress);
        when(withProgress.withDuplicateDocumentHash(true)).thenReturn(flagged);

        assertThat(assembler.toResponse(caseFile)).isSameAs(flagged);

        ArgumentCaptor<CaseNextActionDecision> decision = ArgumentCaptor.forClass(CaseNextActionDecision.class);
        verify(mapped).withProgress(decision.capture());
        assertThat(decision.getValue().nextAction()).isEqualTo(CaseNextAction.NONE);
        verify(withProgress).withDuplicateDocumentHash(true);
    }

    @Test
    void toResponsesLeavesDuplicateFlagFalseWhenSingleApplicant() {
        UUID orgId = UUID.randomUUID();
        CaseFile caseFile = caseFile(CaseStatus.COMPLETED);
        Applicant same = applicant();
        CaseDocument first = document(orgId, "hash-1", caseFile, same);
        CaseDocument second = document(orgId, "hash-1", caseFile, same);

        when(caseDocumentRepository.findByCaseFile_IdIn(List.of(caseFile.getId())))
                .thenReturn(List.of(first, second));
        when(caseOrderRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseAppointmentRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseDocumentRepository.findByOrganizationIdAndSha256In(eq(orgId), eq(List.of("hash-1"))))
                .thenReturn(List.of(first, second));

        CaseResponse mapped = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse withProgress = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse unflagged = org.mockito.Mockito.mock(CaseResponse.class);
        when(caseMapper.toResponse(caseFile)).thenReturn(mapped);
        when(mapped.withProgress(any())).thenReturn(withProgress);
        when(withProgress.withDuplicateDocumentHash(false)).thenReturn(unflagged);

        assertThat(assembler.toResponses(List.of(caseFile))).containsExactly(unflagged);
        verify(withProgress).withDuplicateDocumentHash(false);
    }

    @Test
    void toResponsesSkipsHashLookupWhenNoDocuments() {
        CaseFile caseFile = caseFile(CaseStatus.CANCELLED);
        when(caseDocumentRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseOrderRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseAppointmentRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());

        CaseResponse mapped = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse withProgress = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse unflagged = org.mockito.Mockito.mock(CaseResponse.class);
        when(caseMapper.toResponse(caseFile)).thenReturn(mapped);
        when(mapped.withProgress(any())).thenReturn(withProgress);
        when(withProgress.withDuplicateDocumentHash(false)).thenReturn(unflagged);

        assertThat(assembler.toResponses(List.of(caseFile))).containsExactly(unflagged);
        verify(caseDocumentRepository, never()).findByOrganizationIdAndSha256In(any(), anyList());
        verify(withProgress).withDuplicateDocumentHash(false);
        verify(withProgress, never()).withDuplicateDocumentHash(true);
    }

    @Test
    void toResponsesAssemblesMultipleCases() {
        CaseFile first = caseFile(CaseStatus.COMPLETED);
        CaseFile second = caseFile(CaseStatus.CANCELLED);
        when(caseDocumentRepository.findByCaseFile_IdIn(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(caseOrderRepository.findByCaseFile_IdIn(anyList())).thenReturn(List.of());
        when(caseAppointmentRepository.findByCaseFile_IdIn(anyList())).thenReturn(List.of());

        CaseResponse mapped1 = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse mapped2 = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse progress1 = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse progress2 = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse out1 = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse out2 = org.mockito.Mockito.mock(CaseResponse.class);
        when(caseMapper.toResponse(first)).thenReturn(mapped1);
        when(caseMapper.toResponse(second)).thenReturn(mapped2);
        when(mapped1.withProgress(any())).thenReturn(progress1);
        when(mapped2.withProgress(any())).thenReturn(progress2);
        when(progress1.withDuplicateDocumentHash(anyBoolean())).thenReturn(out1);
        when(progress2.withDuplicateDocumentHash(anyBoolean())).thenReturn(out2);

        assertThat(assembler.toResponses(List.of(first, second))).containsExactly(out1, out2);
    }

    @Test
    void toResponsesGroupsOrdersAndAppointmentsPerCase() {
        CaseFile caseFile = caseFile(CaseStatus.COMPLETED);
        com.konselyavisa.payment.domain.CaseOrder order = new com.konselyavisa.payment.domain.CaseOrder();
        order.setCaseFile(caseFile);
        com.konselyavisa.appointment.domain.CaseAppointment appointment =
                new com.konselyavisa.appointment.domain.CaseAppointment();
        appointment.setCaseFile(caseFile);

        when(caseDocumentRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of());
        when(caseOrderRepository.findByCaseFile_IdIn(List.of(caseFile.getId()))).thenReturn(List.of(order));
        when(caseAppointmentRepository.findByCaseFile_IdIn(List.of(caseFile.getId())))
                .thenReturn(List.of(appointment));

        CaseResponse mapped = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse withProgress = org.mockito.Mockito.mock(CaseResponse.class);
        CaseResponse out = org.mockito.Mockito.mock(CaseResponse.class);
        when(caseMapper.toResponse(caseFile)).thenReturn(mapped);
        when(mapped.withProgress(any())).thenReturn(withProgress);
        when(withProgress.withDuplicateDocumentHash(false)).thenReturn(out);

        assertThat(assembler.toResponses(List.of(caseFile))).containsExactly(out);
        verify(caseOrderRepository).findByCaseFile_IdIn(List.of(caseFile.getId()));
        verify(caseAppointmentRepository).findByCaseFile_IdIn(List.of(caseFile.getId()));
    }

    private static CaseFile caseFile(CaseStatus status) {
        CaseFile caseFile = new CaseFile();
        caseFile.setId(UUID.randomUUID());
        caseFile.setStatus(status);
        caseFile.setApplicant(applicant());
        return caseFile;
    }

    private static Applicant applicant() {
        Applicant applicant = new Applicant();
        applicant.setId(UUID.randomUUID());
        return applicant;
    }

    private static CaseDocument document(UUID orgId, String sha256, CaseFile caseFile, Applicant applicant) {
        caseFile.setApplicant(applicant);
        CaseDocument document = new CaseDocument();
        document.setId(UUID.randomUUID());
        document.setOrganizationId(orgId);
        document.setSha256(sha256);
        document.setRequirementCode("PASSPORT");
        document.setCaseFile(caseFile);
        return document;
    }
}
