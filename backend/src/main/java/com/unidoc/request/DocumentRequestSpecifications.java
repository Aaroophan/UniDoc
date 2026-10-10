package com.unidoc.request;

import com.unidoc.profile.StudentProfile;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentRequestSpecifications {

    private DocumentRequestSpecifications() {
    }

    public static Specification<DocumentRequest> forStudent(
            long studentUserId,
            RequestStatus status,
            DeliveryMethod deliveryMethod
    ) {
        List<Specification<DocumentRequest>> filters = new ArrayList<>();
        filters.add((root, query, builder) -> builder.equal(root.get("student").get("id"), studentUserId));

        if (status != null) {
            filters.add((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (deliveryMethod != null) {
            filters.add((root, query, builder) -> builder.equal(root.get("deliveryMethod"), deliveryMethod));
        }

        return combine(filters);
    }

    public static Specification<DocumentRequest> forStaffOrAdmin(
            String queryText,
            RequestStatus status,
            Long documentTypeId,
            DeliveryMethod deliveryMethod,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            LocalDate requiredDate
    ) {
        List<Specification<DocumentRequest>> filters = new ArrayList<>();

        if (queryText != null && !queryText.isBlank()) {
            filters.add(matchesRequestOrStudent(queryText));
        }
        if (status != null) {
            filters.add((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (documentTypeId != null) {
            filters.add((root, query, builder) -> builder.equal(
                    root.get("documentType").get("id"),
                    documentTypeId
            ));
        }
        if (deliveryMethod != null) {
            filters.add((root, query, builder) -> builder.equal(root.get("deliveryMethod"), deliveryMethod));
        }
        if (submittedFrom != null) {
            Instant start = submittedFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
            filters.add((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (submittedTo != null) {
            Instant exclusiveEnd = submittedTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            filters.add((root, query, builder) -> builder.lessThan(root.get("createdAt"), exclusiveEnd));
        }
        if (requiredDate != null) {
            filters.add((root, query, builder) -> builder.equal(root.get("requiredDate"), requiredDate));
        }

        return combine(filters);
    }

    private static Specification<DocumentRequest> matchesRequestOrStudent(String queryText) {
        String pattern = "%" + escapeLike(queryText.trim().toLowerCase(Locale.ROOT)) + "%";

        return (root, query, builder) -> {
            Path<Object> student = root.get("student");
            Subquery<Long> registrationMatch = query.subquery(Long.class);
            Root<StudentProfile> profile = registrationMatch.from(StudentProfile.class);
            registrationMatch.select(profile.get("user").get("id"));
            registrationMatch.where(
                    builder.equal(profile.get("user").get("id"), student.get("id")),
                    builder.like(builder.lower(profile.get("registrationNumber")), pattern, '\\')
            );

            return builder.or(
                    builder.like(builder.lower(root.get("requestNumber")), pattern, '\\'),
                    builder.like(builder.lower(student.get("fullName")), pattern, '\\'),
                    builder.like(builder.lower(student.get("email")), pattern, '\\'),
                    builder.exists(registrationMatch)
            );
        };
    }

    private static Specification<DocumentRequest> combine(List<Specification<DocumentRequest>> filters) {
        return filters.stream()
                .reduce(Specification::and)
                .orElse((root, query, builder) -> builder.conjunction());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
