package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaseListUrgencyTest {

    @Test
    void booksAndUploadsAreActionRequired() {
        assertThat(CaseListUrgency.groupOf(CaseStatus.CREATED, CaseNextAction.UPLOAD_DOCUMENTS))
                .isEqualTo(CaseListUrgencyGroup.ACTION_REQUIRED);
        assertThat(CaseListUrgency.groupOf(CaseStatus.IN_PROGRESS, CaseNextAction.BOOK_APPOINTMENT))
                .isEqualTo(CaseListUrgencyGroup.ACTION_REQUIRED);
        assertThat(CaseListUrgency.groupOf(CaseStatus.CORRECTION_REQUESTED, CaseNextAction.CORRECT_DOCUMENTS))
                .isEqualTo(CaseListUrgencyGroup.ACTION_REQUIRED);
        assertThat(CaseListUrgency.groupOf(CaseStatus.IN_PROGRESS, CaseNextAction.PAY))
                .isEqualTo(CaseListUrgencyGroup.ACTION_REQUIRED);
    }

    @Test
    void processingAndManualPaymentAreInInstruction() {
        assertThat(CaseListUrgency.groupOf(CaseStatus.IN_PROGRESS, CaseNextAction.WAIT_PROCESSING))
                .isEqualTo(CaseListUrgencyGroup.IN_INSTRUCTION);
        assertThat(CaseListUrgency.groupOf(CaseStatus.IN_PROGRESS, CaseNextAction.WAIT_MANUAL_PAYMENT))
                .isEqualTo(CaseListUrgencyGroup.IN_INSTRUCTION);
    }

    @Test
    void completedAndCancelledAreClosed() {
        assertThat(CaseListUrgency.groupOf(CaseStatus.COMPLETED, CaseNextAction.NONE))
                .isEqualTo(CaseListUrgencyGroup.CLOSED);
        assertThat(CaseListUrgency.groupOf(CaseStatus.CANCELLED, CaseNextAction.NONE))
                .isEqualTo(CaseListUrgencyGroup.CLOSED);
    }

    @Test
    void actionKeysFollowNextAction() {
        assertThat(CaseListUrgency.actionMessageKey(CaseNextAction.BOOK_APPOINTMENT))
                .isEqualTo("case.list_action.BOOK_APPOINTMENT");
        assertThat(CaseListUrgency.subtitleMessageKey(CaseNextAction.WAIT_PROCESSING))
                .isEqualTo("case.list_subtitle.WAIT_PROCESSING");
    }
}
