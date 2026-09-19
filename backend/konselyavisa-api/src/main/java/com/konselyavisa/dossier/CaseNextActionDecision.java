package com.konselyavisa.dossier;

public record CaseNextActionDecision(CaseNextAction nextAction, String nextActionMessageKey, String correctionMessageKey) {

    public static CaseNextActionDecision of(CaseNextAction nextAction, String correctionMessageKey) {
        return new CaseNextActionDecision(nextAction, "case.next_action." + nextAction.name(), correctionMessageKey);
    }
}
