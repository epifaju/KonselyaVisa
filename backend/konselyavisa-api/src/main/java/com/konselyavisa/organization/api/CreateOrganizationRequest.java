package com.konselyavisa.organization.api;

import com.konselyavisa.organization.domain.OrganizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank
                @Size(max = 100)
                @Pattern(regexp = "\\A[a-z0-9]+(?:-[a-z0-9]+)*\\z")
                String slug,
        @NotEmpty Map<String, String> nameI18n,
        @Size(max = 5) String defaultLocale,
        @Size(min = 3, max = 3) String defaultCurrency,
        Map<String, Object> settings,
        OrganizationStatus status) {}
