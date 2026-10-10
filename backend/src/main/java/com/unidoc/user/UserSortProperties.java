package com.unidoc.user;

import java.util.Map;
import java.util.Set;

public final class UserSortProperties {

    public static final String DEFAULT = "createdAt";
    public static final Map<String, String> MAPPINGS = Map.of(
            "createdAt", "createdAt",
            "fullName", "fullName",
            "email", "email",
            "role", "role",
            "status", "status"
    );
    public static final Set<String> ALLOWED = MAPPINGS.keySet();

    private UserSortProperties() {
    }
}
