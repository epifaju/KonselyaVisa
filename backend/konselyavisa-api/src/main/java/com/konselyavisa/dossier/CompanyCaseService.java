package com.konselyavisa.dossier;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.CompanyCaseSummaryResponse;
import com.konselyavisa.identity.CurrentUser;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyCaseService {

    private final CaseFileRepository caseFileRepository;

    public CompanyCaseService(CaseFileRepository caseFileRepository) {
        this.caseFileRepository = caseFileRepository;
    }

    @Transactional(readOnly = true)
    public CompanyCaseSummaryResponse summary() {
        if (!CurrentUser.isCompanyWorkspace()) {
            throw BusinessException.forbidden("error.access.denied");
        }
        List<CaseStatusCount> rows;
        if (CurrentUser.isCompanyCollaborator()) {
            String username = CurrentUser.username();
            if (username == null) {
                throw BusinessException.forbidden("error.access.denied");
            }
            rows = caseFileRepository.countGroupedByCreatedBy(username);
        } else {
            rows = caseFileRepository.countGroupedByCreatedByRoleIn(List.of("COMPANY_USER", "COMPANY_ADMIN"));
        }
        Map<CaseStatus, Long> byStatus = new EnumMap<>(CaseStatus.class);
        for (CaseStatus status : CaseStatus.values()) {
            byStatus.put(status, 0L);
        }
        long total = 0;
        for (CaseStatusCount row : rows) {
            if (row.getStatus() == null) {
                continue;
            }
            byStatus.put(row.getStatus(), row.getTotal());
            total += row.getTotal();
        }
        return new CompanyCaseSummaryResponse(total, byStatus);
    }
}
