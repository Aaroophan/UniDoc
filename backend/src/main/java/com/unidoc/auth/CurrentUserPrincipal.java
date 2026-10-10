package com.unidoc.auth;

import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;

public record CurrentUserPrincipal(
        long userId,
        UserRole role,
        String email,
        UserStatus status,
        int authVersion
) {
}
