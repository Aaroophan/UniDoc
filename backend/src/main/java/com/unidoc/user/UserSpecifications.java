package com.unidoc.user;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> filtered(String query, UserRole role, UserStatus status) {
        List<Specification<User>> filters = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, criteriaQuery, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), pattern, '\\'),
                    builder.like(builder.lower(root.get("fullName")), pattern, '\\')
            ));
        }
        if (role != null) {
            filters.add((root, criteriaQuery, builder) -> builder.equal(root.get("role"), role));
        }
        if (status != null) {
            filters.add((root, criteriaQuery, builder) -> builder.equal(root.get("status"), status));
        }

        return combine(filters);
    }

    private static Specification<User> combine(List<Specification<User>> filters) {
        return filters.stream()
                .reduce(Specification::and)
                .orElse((root, query, builder) -> builder.conjunction());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
