package com.unidoc.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Only the seven editable profile fields. Other properties cannot change account identity. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateStudentProfileRequest(
        @NotBlank(message = "Full name is required.")
        @TrimmedSize(min = 2, max = 150, message = "Full name must be between 2 and 150 characters.")
        String fullName,

        @NotBlank(message = "Registration number is required.")
        @TrimmedSize(min = 2, max = 50, message = "Registration number must be between 2 and 50 characters.")
        @Pattern(regexp = "[ \\t]*[A-Za-z0-9/-]+[ \\t]*",
                 message = "Use only letters, numbers, slash and hyphen.")
        String registrationNumber,

        @ValidOptionalPhone
        String phone,

        @NotBlank(message = "Faculty is required.")
        @TrimmedSize(min = 2, max = 100, message = "Faculty must be between 2 and 100 characters.")
        String faculty,

        @NotBlank(message = "Department is required.")
        @TrimmedSize(min = 2, max = 100, message = "Department must be between 2 and 100 characters.")
        String department,

        @NotBlank(message = "Degree programme is required.")
        @TrimmedSize(min = 2, max = 150, message = "Degree programme must be between 2 and 150 characters.")
        String degreeProgramme,

        @NotNull(message = "Academic year is required.")
        @Min(value = 1, message = "Academic year must be between 1 and 8.")
        @Max(value = 8, message = "Academic year must be between 1 and 8.")
        Integer academicYear
) {
}
