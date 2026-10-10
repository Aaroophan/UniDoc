package com.unidoc.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, PasswordConfirmation> {

    @Override
    public boolean isValid(PasswordConfirmation value, ConstraintValidatorContext context) {
        if (value == null || value.password() == null || value.confirmPassword() == null) {
            return true;
        }
        if (Objects.equals(value.password(), value.confirmPassword())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("confirmPassword")
                .addConstraintViolation();
        return false;
    }
}
