package com.konselyavisa.privacy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record AcceptOrganizationDpaRequest(
        @NotBlank @Size(max = 40) String version,
        Map<String, String> controllerNameI18n,
        @Size(max = 500) String documentUri) {}
