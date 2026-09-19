package com.konselyavisa.appointment.api;

import com.konselyavisa.appointment.AppointmentService;
import com.konselyavisa.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/cases/{caseId}/appointments")
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<AppointmentResponse> book(
            @PathVariable UUID caseId, @Valid @RequestBody BookAppointmentRequest request) {
        return ApiResponse.ok(appointmentService.book(caseId, request), "appointment.booked");
    }

    @GetMapping("/cases/{caseId}/appointments")
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<AppointmentResponse>> listByCase(@PathVariable UUID caseId) {
        return ApiResponse.ok(appointmentService.listByCase(caseId));
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<AppointmentResponse> cancel(@PathVariable UUID appointmentId) {
        return ApiResponse.ok(appointmentService.cancel(appointmentId), "appointment.cancelled");
    }
}
