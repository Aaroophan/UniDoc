package com.unidoc.request;

/** Only the identity fields required by the student detail contract. */
public record StudentRequestStudentSummary(
        Long id,
        String fullName,
        String email,
        String registrationNumber
) {
}
