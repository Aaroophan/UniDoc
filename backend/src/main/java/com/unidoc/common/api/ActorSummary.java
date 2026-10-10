package com.unidoc.common.api;

import com.unidoc.user.UserRole;

public record ActorSummary(
        Long id,
        String fullName,
        UserRole role
) {
}
