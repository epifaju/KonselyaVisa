package com.konselyavisa.guest.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.guest.GuestAccountRegistrationRequest;
import com.konselyavisa.guest.GuestAccountRegistrationResponse;
import com.konselyavisa.guest.GuestAccountRegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicGuestAccountController {

    private final GuestAccountRegistrationService guestAccountRegistrationService;

    public PublicGuestAccountController(GuestAccountRegistrationService guestAccountRegistrationService) {
        this.guestAccountRegistrationService = guestAccountRegistrationService;
    }

    @PostMapping("/account-registrations")
    public ApiResponse<GuestAccountRegistrationResponse> register(
            @Valid @RequestBody GuestAccountRegistrationRequest request) {
        return ApiResponse.ok(guestAccountRegistrationService.register(request), "guest.account.created");
    }
}
