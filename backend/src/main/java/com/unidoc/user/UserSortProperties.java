package com.unidoc.user;

import java.util.Set;

public final class UserSortProperties {

    public static final String DEFAULT = "createdAt";
    public static final Set<String> ALLOWED = Set.of("createdAt", "fullName", "email", "role", "status");

    private UserSortProperties() {
    }
}
