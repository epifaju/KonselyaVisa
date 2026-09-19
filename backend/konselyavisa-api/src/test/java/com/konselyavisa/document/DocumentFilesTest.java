package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.domain.ProcedureVersion;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentFilesTest {

    @Test
    void sha256IsStableHex() throws Exception {
        assertThat(DocumentFiles.sha256Hex("konselya".getBytes()))
                .isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest("konselya".getBytes())));
    }

    @Test
    void sanitizesPathAndAllowsDeclaredRequirement() throws Exception {
        assertThat(DocumentFiles.sanitizeOriginalFilename("C:\\\\temp\\\\passeport.pdf")).isEqualTo("passeport.pdf");
        assertThat(DocumentFiles.isAllowedContentType("application/pdf")).isTrue();
        assertThat(DocumentFiles.isAllowedContentType("application/zip")).isFalse();

        ProcedureVersion version = new ProcedureVersion();
        version.setDocumentRequirements(List.of(Map.of("code", "PASSPORT")));
        assertThat(DocumentRequirementCodes.isDeclared(version, "PASSPORT")).isTrue();
        assertThat(DocumentRequirementCodes.isDeclared(version, "BANK_STATEMENT")).isFalse();

        UUID org = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        assertThat(DocumentFiles.storageKey(org, caseId, docId, "photo.png"))
                .isEqualTo(org + "/" + caseId + "/" + docId + "/photo.png");
    }
}
