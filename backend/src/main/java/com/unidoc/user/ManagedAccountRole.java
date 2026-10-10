package com.unidoc.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ManagedAccountRoleValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedAccountRole {
    String message() default "Role must be STAFF or ADMIN.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
