package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class GuestAccountPasswordRulesTest {

    @Test
    void rejectsShortOrNonMixedPasswords() {
        assertThatThrownBy(() -> GuestAccountPasswordRules.requireStrong("short1Aa", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.password_weak");
        assertThatThrownBy(() -> GuestAccountPasswordRules.requireStrong("abcdefghijkl", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.password_weak");
        assertThatThrownBy(() -> GuestAccountPasswordRules.requireStrong("user@example.com", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.password_weak");
        GuestAccountPasswordRules.requireStrong("CitizenDev!23", "user@example.com");
    }
}
