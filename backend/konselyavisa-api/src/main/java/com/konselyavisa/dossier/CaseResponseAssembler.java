package com.konselyavisa.dossier;

import com.konselyavisa.appointment.domain.CaseAppointment;
import com.konselyavisa.appointment.persistence.CaseAppointmentRepository;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.api.CaseMapper;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.payment.domain.CaseOrder;
import com.konselyavisa.payment.persistence.CaseOrderRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CaseResponseAssembler {

    private final CaseMapper caseMapper;
    private final CaseDocumentRepository caseDocumentRepository;
    private final CaseOrderRepository caseOrderRepository;
    private final CaseAppointmentRepository caseAppointmentRepository;

    public CaseResponseAssembler(
            CaseMapper caseMapper,
            CaseDocumentRepository caseDocumentRepository,
            CaseOrderRepository caseOrderRepository,
            CaseAppointmentRepository caseAppointmentRepository) {
        this.caseMapper = caseMapper;
        this.caseDocumentRepository = caseDocumentRepository;
        this.caseOrderRepository = caseOrderRepository;
        this.caseAppointmentRepository = caseAppointmentRepository;
    }

    public CaseResponse toResponse(CaseFile caseFile) {
        return toResponses(List.of(caseFile)).getFirst();
    }

    public List<CaseResponse> toResponses(List<CaseFile> caseFiles) {
        if (caseFiles.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = caseFiles.stream().map(CaseFile::getId).toList();
        Map<UUID, List<CaseDocument>> documents = groupDocuments(caseDocumentRepository.findByCaseFile_IdIn(ids));
        Map<UUID, List<CaseOrder>> orders = groupOrders(caseOrderRepository.findByCaseFile_IdIn(ids));
        Map<UUID, List<CaseAppointment>> appointments =
                groupAppointments(caseAppointmentRepository.findByCaseFile_IdIn(ids));
        List<CaseResponse> responses = new ArrayList<>(caseFiles.size());
        for (CaseFile caseFile : caseFiles) {
            CaseResponse mapped = caseMapper.toResponse(caseFile);
            CaseNextActionDecision decision = CaseNextActionResolver.resolve(
                    caseFile,
                    documents.getOrDefault(caseFile.getId(), List.of()),
                    orders.getOrDefault(caseFile.getId(), List.of()),
                    appointments.getOrDefault(caseFile.getId(), List.of()));
            responses.add(mapped.withProgress(decision));
        }
        return responses;
    }

    private static Map<UUID, List<CaseDocument>> groupDocuments(List<CaseDocument> documents) {
        Map<UUID, List<CaseDocument>> grouped = new HashMap<>();
        for (CaseDocument document : documents) {
            grouped.computeIfAbsent(document.getCaseFile().getId(), ignored -> new ArrayList<>()).add(document);
        }
        return grouped;
    }

    private static Map<UUID, List<CaseOrder>> groupOrders(List<CaseOrder> orders) {
        Map<UUID, List<CaseOrder>> grouped = new HashMap<>();
        for (CaseOrder order : orders) {
            grouped.computeIfAbsent(order.getCaseFile().getId(), ignored -> new ArrayList<>()).add(order);
        }
        return grouped;
    }

    private static Map<UUID, List<CaseAppointment>> groupAppointments(List<CaseAppointment> appointments) {
        Map<UUID, List<CaseAppointment>> grouped = new HashMap<>();
        for (CaseAppointment appointment : appointments) {
            grouped.computeIfAbsent(appointment.getCaseFile().getId(), ignored -> new ArrayList<>())
                    .add(appointment);
        }
        return grouped;
    }
}
