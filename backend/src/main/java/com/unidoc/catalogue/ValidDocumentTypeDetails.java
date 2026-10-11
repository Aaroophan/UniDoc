package com.unidoc.catalogue;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = DocumentTypeDetailsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDocumentTypeDetails {
    String message() default "Document type delivery configuration is invalid.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
