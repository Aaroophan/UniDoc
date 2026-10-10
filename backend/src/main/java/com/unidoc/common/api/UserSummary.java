package com.unidoc.common.api;

import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;

import java.time.Instant;

public record UserSummary(
        Long id,
        String email,
        String fullName,
        UserRole role,
        UserStatus status,
        Instant createdAt
) {
}
