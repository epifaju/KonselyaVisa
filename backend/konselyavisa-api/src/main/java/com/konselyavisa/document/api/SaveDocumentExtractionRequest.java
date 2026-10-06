package com.konselyavisa.document.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record SaveDocumentExtractionRequest(
        @NotEmpty Map<String, String> extractedFields, Map<String, String> documentValidations) {}
