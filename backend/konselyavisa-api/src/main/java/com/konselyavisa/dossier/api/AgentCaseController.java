package com.konselyavisa.dossier.api;

import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.dossier.AgentCaseQueryService;
import com.konselyavisa.dossier.CaseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/cases")
public class AgentCaseController {

    private final AgentCaseQueryService agentCaseQueryService;

    public AgentCaseController(AgentCaseQueryService agentCaseQueryService) {
        this.agentCaseQueryService = agentCaseQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public PageResponse<CaseResponse> list(
            @RequestParam(required = false) CaseStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return agentCaseQueryService.list(status, pageable);
    }
}
