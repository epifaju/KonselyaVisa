package com.konselyavisa.dossier;

import com.konselyavisa.appointment.domain.AppointmentStatus;
import com.konselyavisa.appointment.domain.CaseAppointment;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.payment.domain.CaseOrder;
import com.konselyavisa.payment.domain.OrderStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CaseNextActionResolver {

    private CaseNextActionResolver() {}

    public static CaseNextActionDecision resolve(
            CaseFile caseFile,
            List<CaseDocument> documents,
            List<CaseOrder> orders,
            List<CaseAppointment> appointments) {
        if (caseFile.getStatus() == CaseStatus.COMPLETED || caseFile.getStatus() == CaseStatus.CANCELLED) {
            return CaseNextActionDecision.of(CaseNextAction.NONE, null);
        }

        Map<String, CaseDocument> latestByCode = latestDocuments(documents);
        String correctionKey = latestCorrectionKey(latestByCode);
        if (caseFile.getStatus() == CaseStatus.CORRECTION_REQUESTED || correctionKey != null) {
            return CaseNextActionDecision.of(CaseNextAction.CORRECT_DOCUMENTS, correctionKey);
        }

        for (String code : CaseRequirementCatalog.requiredCodes(caseFile.getProcedureVersion())) {
            CaseDocument document = latestByCode.get(code);
            if (document == null
                    || document.getStatus() == DocumentStatus.REJECTED
                    || document.getStatus() == DocumentStatus.REPLACED) {
                return CaseNextActionDecision.of(CaseNextAction.UPLOAD_DOCUMENTS, null);
            }
        }

        OrderStatus payment = latestPaymentStatus(orders);
        if (payment == null || payment == OrderStatus.CANCELLED) {
            return CaseNextActionDecision.of(CaseNextAction.PAY, null);
        }
        if (payment == OrderStatus.PENDING_PAYMENT) {
            return CaseNextActionDecision.of(CaseNextAction.PAY, null);
        }
        if (payment == OrderStatus.PENDING_MANUAL) {
            return CaseNextActionDecision.of(CaseNextAction.WAIT_MANUAL_PAYMENT, null);
        }

        boolean booked = appointments.stream().anyMatch(item -> item.getStatus() == AppointmentStatus.BOOKED);
        if (!booked) {
            return CaseNextActionDecision.of(CaseNextAction.BOOK_APPOINTMENT, null);
        }
        return CaseNextActionDecision.of(CaseNextAction.WAIT_PROCESSING, null);
    }

    private static Map<String, CaseDocument> latestDocuments(List<CaseDocument> documents) {
        Map<String, CaseDocument> latest = new HashMap<>();
        documents.stream()
                .sorted(Comparator.comparing(CaseDocument::getCreatedAt, Comparator.nullsFirst(Instant::compareTo)))
                .forEach(document -> latest.put(document.getRequirementCode(), document));
        return latest;
    }

    private static String latestCorrectionKey(Map<String, CaseDocument> latestByCode) {
        return latestByCode.values().stream()
                .filter(document -> document.getStatus() == DocumentStatus.CORRECTION_REQUESTED)
                .max(Comparator.comparing(CaseDocument::getUpdatedAt, Comparator.nullsFirst(Instant::compareTo)))
                .map(CaseDocument::getReviewMessageKey)
                .orElse(null);
    }

    private static OrderStatus latestPaymentStatus(List<CaseOrder> orders) {
        return orders.stream()
                .max(Comparator.comparing(CaseOrder::getCreatedAt, Comparator.nullsFirst(Instant::compareTo)))
                .map(CaseOrder::getStatus)
                .orElse(null);
    }
}
