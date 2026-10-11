package com.unidoc.catalogue;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentTypeDetailsValidator implements ConstraintValidator<ValidDocumentTypeDetails, UpsertDocumentTypeRequest> {

    @Override
    public boolean isValid(UpsertDocumentTypeRequest value, ConstraintValidatorContext context) {
        if (value == null) return true;
        boolean valid = true;
        if (Boolean.FALSE.equals(value.electronicAvailable()) && Boolean.FALSE.equals(value.physicalAvailable())) {
            fieldError(context, "electronicAvailable", "Enable at least one delivery method.");
            valid = false;
        }
        if (Boolean.TRUE.equals(value.physicalAvailable())
                && (value.collectionLocation() == null || value.collectionLocation().isBlank())) {
            fieldError(context, "collectionLocation", "Collection location is required for physical delivery.");
            valid = false;
        }
        return valid;
    }

    private static void fieldError(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field).addConstraintViolation();
    }
}
