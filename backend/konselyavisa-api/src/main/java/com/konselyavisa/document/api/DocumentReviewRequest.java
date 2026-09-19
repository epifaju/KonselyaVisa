package com.konselyavisa.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentReviewRequest(
        @NotBlank @Size(max = 500) String reason) {}
