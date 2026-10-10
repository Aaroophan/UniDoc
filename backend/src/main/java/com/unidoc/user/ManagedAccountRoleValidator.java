package com.unidoc.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ManagedAccountRoleValidator implements ConstraintValidator<ManagedAccountRole, UserRole> {
    @Override
    public boolean isValid(UserRole value, ConstraintValidatorContext context) {
        return value == null || value == UserRole.STAFF || value == UserRole.ADMIN;
    }
}
