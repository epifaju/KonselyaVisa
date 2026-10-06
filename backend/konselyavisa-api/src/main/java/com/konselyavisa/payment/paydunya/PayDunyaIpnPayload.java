package com.konselyavisa.payment.paydunya;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** Extracts the PayDunya IPN {@code data} object from form or JSON webhook bodies. */
public final class PayDunyaIpnPayload {

    private PayDunyaIpnPayload() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extract(Map<String, Object> fields, ObjectMapper objectMapper) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Object raw = fields.get("data");
        if (raw instanceof Map<?, ?> map) {
            return castMap(map);
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                try {
                    return objectMapper.readValue(trimmed, new TypeReference<>() {});
                } catch (Exception ignored) {
                    // fall through to bracket keys / flat fields
                }
            }
        }
        Map<String, Object> fromBrackets = nestBracketKeys(fields, "data");
        if (!fromBrackets.isEmpty()) {
            return fromBrackets;
        }
        if (fields.containsKey("hash") || fields.containsKey("status") || fields.containsKey("invoice")) {
            return fields;
        }
        return Map.of();
    }

    static Map<String, Object> nestBracketKeys(Map<String, Object> flat, String root) {
        Map<String, Object> nested = new LinkedHashMap<>();
        String prefix = root + "[";
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix)) {
                continue;
            }
            putPath(nested, pathSegments(key.substring(root.length())), entry.getValue());
        }
        return nested;
    }

    private static String[] pathSegments(String bracketPath) {
        // "[hash]" or "[invoice][token]"
        String cleaned = bracketPath.replace("][", ".").replace("[", "").replace("]", "");
        return cleaned.split("\\.");
    }

    @SuppressWarnings("unchecked")
    private static void putPath(Map<String, Object> root, String[] segments, Object value) {
        Map<String, Object> current = root;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isBlank()) {
                continue;
            }
            if (i == segments.length - 1) {
                current.put(segment, value);
                return;
            }
            Object next = current.get(segment);
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> child = new LinkedHashMap<>();
                current.put(segment, child);
                current = child;
            } else {
                current = (Map<String, Object>) next;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                copy.put(String.valueOf(key), value);
            }
        });
        return copy;
    }
}
