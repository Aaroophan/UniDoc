package com.unidoc.auth;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
