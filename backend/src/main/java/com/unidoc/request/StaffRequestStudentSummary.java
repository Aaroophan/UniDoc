package com.unidoc.request;

/** Staff-only student/account and optional academic profile fields. No auth secrets. */
public record StaffRequestStudentSummary(
        Long id,
        String fullName,
        String email,
        String registrationNumber,
        String phone,
        String faculty,
        String department,
        String degreeProgramme,
        Integer academicYear
) {
}
