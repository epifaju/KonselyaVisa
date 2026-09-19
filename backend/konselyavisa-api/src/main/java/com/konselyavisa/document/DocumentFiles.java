package com.konselyavisa.document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class DocumentFiles {

    public static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

    private DocumentFiles() {}

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    public static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document.bin";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\"]", "_").trim();
        if (name.isBlank() || name.contains("..")) {
            return "document.bin";
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > 255) {
            return "document.bin";
        }
        return name;
    }

    public static String storageKey(UUID organizationId, UUID caseId, UUID documentId, String originalFilename) {
        String safe = sanitizeOriginalFilename(originalFilename).replace(' ', '_');
        return organizationId + "/" + caseId + "/" + documentId + "/" + safe;
    }
}
