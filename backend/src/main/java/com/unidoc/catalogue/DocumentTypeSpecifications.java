package com.unidoc.catalogue;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentTypeSpecifications {

    private DocumentTypeSpecifications() {
    }

    public static Specification<DocumentType> filtered(String query, Boolean active) {
        List<Specification<DocumentType>> filters = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, criteriaQuery, builder) -> builder.or(
                    builder.like(builder.lower(root.get("code")), pattern, '\\'),
                    builder.like(builder.lower(root.get("name")), pattern, '\\')
            ));
        }
        if (active != null) {
            filters.add((root, criteriaQuery, builder) -> builder.equal(root.get("active"), active));
        }

        return filters.stream()
                .reduce(Specification::and)
                .orElse((root, criteriaQuery, builder) -> builder.conjunction());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
