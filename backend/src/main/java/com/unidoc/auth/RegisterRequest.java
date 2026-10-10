package com.unidoc.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@PasswordsMatch
public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @TrimmedSize(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @NormalizedEmailAddress
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        @ValidPassword
        String password,

        @NotBlank(message = "Password confirmation is required.")
        String confirmPassword
) implements PasswordConfirmation {
}
