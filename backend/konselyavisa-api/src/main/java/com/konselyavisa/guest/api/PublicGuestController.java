package com.konselyavisa.guest.api;

import com.konselyavisa.catalog.service.ProcedureService;
import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.catalog.api.ProcedureResponse;
import com.konselyavisa.guest.GuestEligibilityEvaluateRequest;
import com.konselyavisa.guest.GuestEligibilityEvaluateResponse;
import com.konselyavisa.guest.GuestEligibilityService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicGuestController {

    private final GuestEligibilityService guestEligibilityService;
    private final ProcedureService procedureService;

    public PublicGuestController(GuestEligibilityService guestEligibilityService, ProcedureService procedureService) {
        this.guestEligibilityService = guestEligibilityService;
        this.procedureService = procedureService;
    }

    @GetMapping("/procedures")
    public PageResponse<ProcedureResponse> procedures(
            @RequestParam(required = false) UUID organizationId, @PageableDefault(size = 50) Pageable pageable) {
        guestEligibilityService.bindOrganization(organizationId);
        return procedureService.search(null, null, null, false, pageable);
    }

    @PostMapping("/eligibility-tickets")
    public ApiResponse<GuestEligibilityEvaluateResponse> evaluate(
            @Valid @RequestBody GuestEligibilityEvaluateRequest request) {
        guestEligibilityService.bindOrganization(request.organizationId());
        GuestEligibilityEvaluateResponse response = guestEligibilityService.evaluate(request);
        return ApiResponse.ok(response, response.eligible() ? "guest.eligible" : "error.eligibility.not_met");
    }
}
