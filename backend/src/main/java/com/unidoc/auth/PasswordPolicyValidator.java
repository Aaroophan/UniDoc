package com.unidoc.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;

        for (int codePoint : value.codePoints().toArray()) {
            upper |= Character.isUpperCase(codePoint);
            lower |= Character.isLowerCase(codePoint);
            digit |= Character.isDigit(codePoint);
            special |= !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
        }

        return upper && lower && digit && special;
    }
}
