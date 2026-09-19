package com.konselyavisa.appointment.api;

import com.konselyavisa.appointment.AppointmentService;
import com.konselyavisa.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/appointment-holds")
public class AppointmentHoldController {

    private final AppointmentService appointmentService;

    public AppointmentHoldController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<AppointmentHoldResponse> hold(
            @PathVariable UUID caseId, @Valid @RequestBody HoldAppointmentRequest request) {
        return ApiResponse.ok(appointmentService.hold(caseId, request), "appointment.held");
    }
}
