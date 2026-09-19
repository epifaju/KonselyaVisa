package com.konselyavisa.payment.cinetpay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 of the CinetPay v2 notify field concatenation (site, trans, date, amount,
 * currency, payment_config, phone, prefix, language, version, page_action, custom, designation).
 */
public final class CinetPayHmac {

    private CinetPayHmac() {}

    public static String canonicalPayload(Map<String, ?> fields) {
        return value(fields, "cpm_site_id")
                + value(fields, "cpm_trans_id")
                + value(fields, "cpm_trans_date")
                + value(fields, "cpm_amount")
                + value(fields, "cpm_currency")
                + value(fields, "cpm_payment_config")
                + value(fields, "cel_phone_num")
                + value(fields, "cpm_phone_prefixe")
                + value(fields, "cpm_language")
                + value(fields, "cpm_version")
                + value(fields, "cpm_page_action")
                + value(fields, "cpm_custom")
                + value(fields, "cpm_designation");
    }

    public static String sign(Map<String, ?> fields, String secretKey) {
        return hmacHex(canonicalPayload(fields), secretKey);
    }

    public static boolean matches(Map<String, ?> fields, String signature, String secretKey) {
        if (signature == null || signature.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String expected = sign(fields, secretKey);
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String hmacHex(String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("cinetpay-hmac", ex);
        }
    }

    private static String value(Map<String, ?> fields, String key) {
        if (fields == null) {
            return "";
        }
        Object raw = fields.get(key);
        return raw == null ? "" : String.valueOf(raw);
    }
}
