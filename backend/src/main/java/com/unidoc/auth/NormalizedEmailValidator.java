package com.unidoc.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class NormalizedEmailValidator implements ConstraintValidator<NormalizedEmailAddress, String> {

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim();
        return normalized.length() <= 254 && SIMPLE_EMAIL.matcher(normalized).matches();
    }
}
