package com.konselyavisa.dossier;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.SupervisorSummaryResponse;
import com.konselyavisa.identity.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupervisorSummaryService {

    private static final List<CaseStatus> OPEN_STATUSES =
            List.of(CaseStatus.CREATED, CaseStatus.IN_PROGRESS, CaseStatus.CORRECTION_REQUESTED);

    private final CaseFileRepository caseFileRepository;

    public SupervisorSummaryService(CaseFileRepository caseFileRepository) {
        this.caseFileRepository = caseFileRepository;
    }

    @Transactional(readOnly = true)
    public SupervisorSummaryResponse summarize() {
        if (!CurrentUser.hasRole("SUPERVISOR")
                && !CurrentUser.hasRole("BUSINESS_ADMIN")
                && !CurrentUser.hasRole("PLATFORM_ADMIN")) {
            throw BusinessException.forbidden("error.access.denied");
        }
        Instant now = Instant.now();
        long open = 0;
        long watch = 0;
        long overdue = 0;
        for (CaseFile caseFile : caseFileRepository.findByStatusIn(OPEN_STATUSES)) {
            open += 1;
            Integer days = caseFile.getProcedureVersion() == null
                    ? null
                    : caseFile.getProcedureVersion().getEstimatedInstructionDays();
            CaseSlaAge.Tone tone = CaseSlaAge.tone(caseFile.getCreatedAt(), caseFile.getStatus(), days, now);
            if (tone == CaseSlaAge.Tone.OVERDUE) {
                overdue += 1;
            } else if (tone == CaseSlaAge.Tone.WARNING) {
                watch += 1;
            }
        }
        return new SupervisorSummaryResponse(open, watch, overdue);
    }
}
