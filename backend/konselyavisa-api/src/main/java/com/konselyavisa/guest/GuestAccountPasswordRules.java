package com.konselyavisa.guest;

import com.konselyavisa.common.exception.BusinessException;

final class GuestAccountPasswordRules {

    private GuestAccountPasswordRules() {}

    static void requireStrong(String password, String email) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw BusinessException.badRequest("error.account.password_weak");
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch)) {
                letter = true;
            } else if (Character.isDigit(ch)) {
                digit = true;
            }
        }
        if (!letter || !digit) {
            throw BusinessException.badRequest("error.account.password_weak");
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            throw BusinessException.badRequest("error.account.password_weak");
        }
    }
}
