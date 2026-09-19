package com.konselyavisa.appointment.api;

import com.konselyavisa.appointment.AppointmentService;
import com.konselyavisa.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointment-slots")
public class AppointmentSlotController {

    private final AppointmentService appointmentService;

    public AppointmentSlotController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<AppointmentSlotResponse> create(@Valid @RequestBody CreateSlotRequest request) {
        return ApiResponse.ok(appointmentService.createSlot(request), "appointment.slot_created");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<AppointmentSlotResponse>> list(
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(appointmentService.listSlots(from, to));
    }

    @PostMapping("/{slotId}/cancel")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<AppointmentSlotResponse> cancel(@PathVariable UUID slotId) {
        return ApiResponse.ok(appointmentService.cancelSlot(slotId), "appointment.slot_cancelled");
    }
}
