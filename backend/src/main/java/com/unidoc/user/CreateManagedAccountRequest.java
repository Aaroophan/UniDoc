package com.unidoc.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.unidoc.auth.NormalizedEmailAddress;
import com.unidoc.auth.TrimmedSize;
import com.unidoc.auth.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateManagedAccountRequest(
        @NotBlank(message = "Full name is required.")
        @TrimmedSize(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,
        @NotBlank(message = "Email is required.")
        @NormalizedEmailAddress
        String email,
        @NotNull(message = "Role is required.")
        @ManagedAccountRole
        UserRole role,
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        @ValidPassword
        String password
) {}
