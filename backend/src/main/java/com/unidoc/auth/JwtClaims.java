package com.unidoc.auth;

import com.unidoc.user.UserRole;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaims {

    private JwtClaims() {
    }

    public static long requirePositiveUserId(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) {
                throw new IllegalArgumentException("JWT subject must be a positive user ID.");
            }
            return id;
        } catch (NullPointerException | NumberFormatException exception) {
            throw new IllegalArgumentException("JWT subject must be a positive user ID.", exception);
        }
    }

    public static UserRole requireRole(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (role == null) {
            throw new IllegalArgumentException("JWT role is missing.");
        }
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT role is unsupported.", exception);
        }
    }

    public static int requireAuthVersion(Jwt jwt) {
        Object raw = jwt.getClaim("authVersion");
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("JWT authVersion is missing or invalid.");
        }
        long value = number.longValue();
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JWT authVersion is invalid.");
        }
        return (int) value;
    }
}
