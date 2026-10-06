package com.konselyavisa.payment.paydunya;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * PayDunya IPN authenticity: {@code data.hash} must equal SHA-512 of the merchant MasterKey
 * (not an HMAC of the payload).
 */
public final class PayDunyaHash {

    private PayDunyaHash() {}

    public static String ofMasterKey(String masterKey) {
        if (masterKey == null) {
            throw new IllegalArgumentException("masterKey");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512").digest(masterKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("paydunya-sha512", ex);
        }
    }

    public static boolean matches(String receivedHash, String masterKey) {
        if (receivedHash == null || receivedHash.isBlank() || masterKey == null || masterKey.isBlank()) {
            return false;
        }
        String expected = ofMasterKey(masterKey);
        String actual = receivedHash.trim().toLowerCase(Locale.ROOT);
        if (expected.length() != actual.length()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
