package com.konselyavisa.document.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractedFieldsCryptoTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    private ExtractedFieldsCrypto crypto;

    @BeforeEach
    void setUp() throws Exception {
        crypto = new ExtractedFieldsCrypto(entityManager, new ObjectMapper());
        lenient().when(entityManager.unwrap(Session.class)).thenReturn(session);
        lenient()
                .doAnswer(inv -> {
                    ReturningWork<?> work = inv.getArgument(0);
                    return work.execute(connection);
                })
                .when(session)
                .doReturningWork(any(ReturningWork.class));
    }

    @Test
    void encryptReturnsNullForEmptyFields() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.encrypt(Map.of())).isNull();
    }

    @Test
    void encryptSendsJsonToPgpSymEncrypt() throws Exception {
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBytes(1)).thenReturn(new byte[] {1, 2, 3});

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("passportNumber", "X123");
        byte[] cipher = crypto.encrypt(fields);

        assertThat(cipher).containsExactly(1, 2, 3);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("pgp_sym_encrypt");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(org.mockito.ArgumentMatchers.eq(1), json.capture());
        assertThat(json.getValue()).contains("passportNumber").contains("X123");
    }

    @Test
    void decryptReturnsEmptyForNullOrBlankCipher() {
        assertThat(crypto.decrypt(null)).isEmpty();
        assertThat(crypto.decrypt(new byte[0])).isEmpty();
    }

    @Test
    void decryptParsesJsonFromPgpSymDecrypt() throws Exception {
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("{\"passportNumber\":\"X123\",\"nationality\":\"SN\"}");

        Map<String, String> fields = crypto.decrypt(new byte[] {9});

        assertThat(fields)
                .containsEntry("passportNumber", "X123")
                .containsEntry("nationality", "SN");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("pgp_sym_decrypt");
        verify(statement).setBytes(1, new byte[] {9});
    }

    @Test
    void decryptReturnsEmptyForBlankOrEmptyJsonObject() throws Exception {
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("   ");

        assertThat(crypto.decrypt(new byte[] {1})).isEmpty();

        when(resultSet.getString(1)).thenReturn("{}");
        assertThat(crypto.decrypt(new byte[] {1})).isEmpty();
    }

    @Test
    void decryptThrowsWhenJsonInvalid() throws Exception {
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("{not-json");

        assertThatThrownBy(() -> crypto.decrypt(new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("extracted-fields-json");
    }
}
