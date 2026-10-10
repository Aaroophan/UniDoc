package com.unidoc.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TrimmedSizeValidator implements ConstraintValidator<TrimmedSize, String> {

    private int min;
    private int max;

    @Override
    public void initialize(TrimmedSize annotation) {
        min = annotation.min();
        max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.trim().length();
        return length >= min && length <= max;
    }
}
