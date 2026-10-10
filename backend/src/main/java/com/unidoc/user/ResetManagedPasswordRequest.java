package com.unidoc.user;

import com.unidoc.auth.PasswordConfirmation;
import com.unidoc.auth.PasswordsMatch;
import com.unidoc.auth.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordsMatch
public record ResetManagedPasswordRequest(
        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        @ValidPassword
        String newPassword,
        @NotBlank(message = "Password confirmation is required.")
        String confirmPassword
) implements PasswordConfirmation {
    @Override
    public String password() { return newPassword; }
}
