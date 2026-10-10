package com.unidoc.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @NormalizedEmailAddress
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password must not exceed 72 characters.")
        String password
) {
}
