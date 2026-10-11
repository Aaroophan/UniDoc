package com.unidoc.catalogue;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enforces limits on canonical stored text rather than unnormalized input length. */
@Constraint(validatedBy = NormalizedCatalogueTextValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NormalizedCatalogueText {
    int min() default 0;
    int max();
    String message() default "Text length is invalid.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
