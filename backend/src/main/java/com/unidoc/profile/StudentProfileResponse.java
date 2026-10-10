package com.unidoc.profile;

import java.time.Instant;

/** Public, student-owned profile representation. Email and userId are immutable here. */
public record StudentProfileResponse(
        Long userId,
        String email,
        String fullName,
        String registrationNumber,
        String phone,
        String faculty,
        String department,
        String degreeProgramme,
        Integer academicYear,
        boolean complete,
        Instant updatedAt
) {
}
