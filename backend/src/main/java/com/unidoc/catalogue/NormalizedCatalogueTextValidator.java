package com.unidoc.catalogue;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NormalizedCatalogueTextValidator implements ConstraintValidator<NormalizedCatalogueText, String> {
    private int min;
    private int max;

    @Override
    public void initialize(NormalizedCatalogueText annotation) {
        min = annotation.min();
        max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // Required inputs also have @NotBlank.
        int length = value.strip().replaceAll("(?U)\\s+", " ").length();
        return length >= min && length <= max;
    }
}
