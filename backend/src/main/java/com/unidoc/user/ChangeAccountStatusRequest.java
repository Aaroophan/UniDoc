package com.unidoc.user;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(
        @NotNull(message = "Account status is required.") UserStatus status
) {}
