package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentExtractionSanitizeTest {

    @Test
    void normalizesKeysAndRejectsInvalidEntries() {
        assertThat(StubDocumentExtractionService.sanitizeRequired(Map.of(" GivenName ", " Awa ")))
                .containsExactly(Map.entry("givenname", "Awa"));
        assertThatThrownBy(() -> StubDocumentExtractionService.sanitizeRequired(Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.extraction_required");
        assertThatThrownBy(() -> StubDocumentExtractionService.sanitizeOptional(Map.of("passport number", "X")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.extraction_invalid");
    }
}
