package com.konselyavisa.dossier;

import com.konselyavisa.dossier.api.CaseResponse;
import java.time.Instant;
import java.util.Comparator;

public final class CaseListUrgency {

    public static final Comparator<CaseResponse> LIST_ORDER = Comparator.comparingInt(
                    (CaseResponse item) -> rank(item.listUrgencyGroup()))
            .thenComparing(CaseListUrgency::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private CaseListUrgency() {}

    public static CaseListUrgencyGroup groupOf(CaseStatus status, CaseNextAction nextAction) {
        if (status == CaseStatus.COMPLETED || status == CaseStatus.CANCELLED) {
            return CaseListUrgencyGroup.CLOSED;
        }
        if (nextAction == CaseNextAction.WAIT_PROCESSING
                || nextAction == CaseNextAction.WAIT_MANUAL_PAYMENT
                || nextAction == CaseNextAction.NONE) {
            return CaseListUrgencyGroup.IN_INSTRUCTION;
        }
        return CaseListUrgencyGroup.ACTION_REQUIRED;
    }

    public static String actionMessageKey(CaseNextAction nextAction) {
        CaseNextAction action = nextAction == null ? CaseNextAction.NONE : nextAction;
        return "case.list_action." + action.name();
    }

    public static String subtitleMessageKey(CaseNextAction nextAction) {
        CaseNextAction action = nextAction == null ? CaseNextAction.NONE : nextAction;
        return "case.list_subtitle." + action.name();
    }

    private static int rank(CaseListUrgencyGroup group) {
        return group == null ? Integer.MAX_VALUE : group.ordinal();
    }

    private static Instant updatedAt(CaseResponse item) {
        return item.updatedAt() != null ? item.updatedAt() : item.createdAt();
    }
}
