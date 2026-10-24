package com.konselyavisa.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubKeycloakCitizenDirectoryTest {

    private StubKeycloakCitizenDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new StubKeycloakCitizenDirectory();
    }

    @Test
    void createCitizenReturnsNormalizedSubject() {
        KeycloakCitizenCreated created =
                directory.createCitizen(" Citizen@Example.COM ", "Secret1!", "Ada", UUID.randomUUID());

        assertThat(created.username()).isEqualTo("citizen@example.com");
        assertThat(created.subject()).isNotBlank();
    }

    @Test
    void createCitizenRejectsBlankPassword() {
        assertThatThrownBy(() -> directory.createCitizen("a@b.c", "  ", "A", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.password_weak");
    }

    @Test
    void createCitizenRejectsDuplicateEmail() {
        UUID orgId = UUID.randomUUID();
        directory.createCitizen("dup@example.com", "Secret1!", "One", orgId);

        assertThatThrownBy(() -> directory.createCitizen(" DUP@example.com ", "Secret1!", "Two", orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.email_exists");
    }
}
