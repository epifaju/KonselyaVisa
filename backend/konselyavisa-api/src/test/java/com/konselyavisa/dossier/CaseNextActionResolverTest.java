package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.appointment.domain.AppointmentStatus;
import com.konselyavisa.appointment.domain.CaseAppointment;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.document.ReviewMessageKeys;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.payment.domain.CaseOrder;
import com.konselyavisa.payment.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaseNextActionResolverTest {

    @Test
    void asksForRequiredDocumentsWhenMissing() {
        CaseNextActionDecision decision =
                CaseNextActionResolver.resolve(openCase(), List.of(), List.of(), List.of());
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.UPLOAD_DOCUMENTS);
        assertThat(decision.nextActionMessageKey()).isEqualTo("case.next_action.UPLOAD_DOCUMENTS");
    }

    @Test
    void asksForCorrectionWhenLatestDocumentNeedsRewrite() {
        CaseDocument passport = document("PASSPORT", DocumentStatus.CORRECTION_REQUESTED);
        passport.setReviewMessageKey(ReviewMessageKeys.DEFAULT_CORRECTION);
        CaseNextActionDecision decision =
                CaseNextActionResolver.resolve(openCase(), List.of(passport, document("PHOTO", DocumentStatus.UPLOADED)), List.of(), List.of());
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.CORRECT_DOCUMENTS);
        assertThat(decision.correctionMessageKey()).isEqualTo(ReviewMessageKeys.DEFAULT_CORRECTION);
    }

    @Test
    void asksForPaymentWhenDocumentsArePresent() {
        CaseNextActionDecision decision = CaseNextActionResolver.resolve(
                openCase(),
                List.of(document("PASSPORT", DocumentStatus.UPLOADED), document("PHOTO", DocumentStatus.ACCEPTED)),
                List.of(),
                List.of());
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.PAY);
    }

    @Test
    void waitsForManualPayment() {
        CaseOrder order = new CaseOrder();
        order.setStatus(OrderStatus.PENDING_MANUAL);
        order.setCreatedAt(Instant.now());
        CaseNextActionDecision decision = CaseNextActionResolver.resolve(
                openCase(),
                List.of(document("PASSPORT", DocumentStatus.UPLOADED), document("PHOTO", DocumentStatus.UPLOADED)),
                List.of(order),
                List.of());
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.WAIT_MANUAL_PAYMENT);
    }

    @Test
    void asksToBookWhenPaid() {
        CaseOrder order = new CaseOrder();
        order.setStatus(OrderStatus.PAID);
        order.setCreatedAt(Instant.now());
        CaseNextActionDecision decision = CaseNextActionResolver.resolve(
                openCase(),
                List.of(document("PASSPORT", DocumentStatus.UPLOADED), document("PHOTO", DocumentStatus.UPLOADED)),
                List.of(order),
                List.of());
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.BOOK_APPOINTMENT);
    }

    @Test
    void waitsWhenPaidAndBooked() {
        CaseOrder order = new CaseOrder();
        order.setStatus(OrderStatus.PAID);
        order.setCreatedAt(Instant.now());
        CaseAppointment appointment = new CaseAppointment();
        appointment.setStatus(AppointmentStatus.BOOKED);
        CaseNextActionDecision decision = CaseNextActionResolver.resolve(
                openCase(),
                List.of(document("PASSPORT", DocumentStatus.UPLOADED), document("PHOTO", DocumentStatus.UPLOADED)),
                List.of(order),
                List.of(appointment));
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.WAIT_PROCESSING);
    }

    @Test
    void noneWhenClosed() {
        CaseFile caseFile = openCase();
        caseFile.setStatus(CaseStatus.COMPLETED);
        CaseNextActionDecision decision =
                CaseNextActionResolver.resolve(caseFile, List.of(), List.of(), List.of());
        assertThat(decision.nextAction()).isEqualTo(CaseNextAction.NONE);
    }

    private static CaseFile openCase() {
        ProcedureVersion version = new ProcedureVersion();
        version.setDocumentRequirements(List.of(
                Map.of("code", "PASSPORT", "required", true),
                Map.of("code", "PHOTO", "required", true)));
        CaseFile caseFile = new CaseFile();
        caseFile.setStatus(CaseStatus.CREATED);
        caseFile.setProcedureVersion(version);
        return caseFile;
    }

    private static CaseDocument document(String code, DocumentStatus status) {
        CaseDocument document = new CaseDocument();
        document.setRequirementCode(code);
        document.setStatus(status);
        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        return document;
    }
}
