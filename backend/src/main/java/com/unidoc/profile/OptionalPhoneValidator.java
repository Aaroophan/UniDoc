package com.unidoc.profile;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OptionalPhoneValidator implements ConstraintValidator<ValidOptionalPhone, String> {
    @Override
    public boolean isValid(String raw, ConstraintValidatorContext context) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String phone = raw.trim();
        return phone.length() >= 7 && phone.length() <= 30
                && phone.matches("[+0-9 ()-]+")
                && phone.chars().anyMatch(character -> character >= '0' && character <= '9');
    }
}
