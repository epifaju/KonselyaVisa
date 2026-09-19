package com.konselyavisa.dossier;

import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.identity.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentCaseQueryService {

    private final CaseFileRepository caseFileRepository;
    private final CaseResponseAssembler caseResponseAssembler;

    public AgentCaseQueryService(CaseFileRepository caseFileRepository, CaseResponseAssembler caseResponseAssembler) {
        this.caseFileRepository = caseFileRepository;
        this.caseResponseAssembler = caseResponseAssembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> list(CaseStatus status, Pageable pageable) {
        if (CurrentUser.isSelfScoped()) {
            throw BusinessException.forbidden("error.access.denied");
        }
        Page<CaseFile> page = status == null
                ? caseFileRepository.findAll(pageable)
                : caseFileRepository.findAllByStatus(status, pageable);
        return new PageResponse<>(
                caseResponseAssembler.toResponses(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
