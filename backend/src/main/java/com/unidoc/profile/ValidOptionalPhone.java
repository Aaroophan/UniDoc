package com.unidoc.profile;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = OptionalPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidOptionalPhone {
    String message() default "Phone must be 7 to 30 characters using numbers, spaces, +, - or parentheses.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
