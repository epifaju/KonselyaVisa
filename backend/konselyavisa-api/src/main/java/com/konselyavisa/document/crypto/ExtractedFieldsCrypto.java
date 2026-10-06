package com.konselyavisa.document.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Component
public class ExtractedFieldsCrypto {

    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public ExtractedFieldsCrypto(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public byte[] encrypt(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        String json = writeJson(fields);
        Session session = entityManager.unwrap(Session.class);
        return session.doReturningWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT pgp_sym_encrypt(
                            ?,
                            current_setting('app.extracted_fields_key'),
                            'compress-algo=1, cipher-algo=aes256')
                    """)) {
                statement.setString(1, json);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBytes(1);
                }
            }
        });
    }

    public Map<String, String> decrypt(byte[] cipher) {
        if (cipher == null || cipher.length == 0) {
            return Map.of();
        }
        Session session = entityManager.unwrap(Session.class);
        String json = session.doReturningWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pgp_sym_decrypt(?, current_setting('app.extracted_fields_key'))")) {
                statement.setBytes(1, cipher);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getString(1);
                }
            }
        });
        return readJson(json);
    }

    private String writeJson(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception ex) {
            throw new IllegalStateException("extracted-fields-json", ex);
        }
    }

    private Map<String, String> readJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, String> parsed = objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), MAP_TYPE);
            return parsed == null || parsed.isEmpty() ? Map.of() : Map.copyOf(parsed);
        } catch (Exception ex) {
            throw new IllegalStateException("extracted-fields-json", ex);
        }
    }
}
