package com.unidoc.auth;

import com.unidoc.common.api.UserSummary;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        UserSummary user
) {
}
