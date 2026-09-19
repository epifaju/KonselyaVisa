package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewMessageKeysTest {

    @Test
    void keepsStructuredKeyOnFirstLine() {
        assertThat(ReviewMessageKeys.fromReason("document.correction.unreadable\nBord flou"))
                .isEqualTo("document.correction.unreadable");
    }

    @Test
    void defaultsFreeText() {
        assertThat(ReviewMessageKeys.fromReason("pièce illisible"))
                .isEqualTo(ReviewMessageKeys.DEFAULT_CORRECTION);
    }
}
