package com.unidoc.profile;

import com.unidoc.auth.SessionRevokedException;
import com.unidoc.common.persistence.Normalization;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class StudentProfileService {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileService.class);

    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    public StudentProfileService(
            StudentProfileRepository profileRepository,
            UserRepository userRepository,
            Validator validator
    ) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getCurrentProfile(long currentUserId) {
        User student = requireStudent(currentUserId);
        return toResponse(student, profileRepository.findByUserId(student.getId()).orElse(null));
    }

    /** First-time creation and later replacement are deliberately the same atomic operation. */
    @Transactional
    public StudentProfileResponse updateCurrentProfile(long currentUserId, UpdateStudentProfileRequest request) {
        User student = requireStudent(currentUserId);
        Set<ConstraintViolation<UpdateStudentProfileRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        String registration = Normalization.registrationNumber(request.registrationNumber());
        StudentProfile profile = profileRepository.findByUserId(student.getId()).orElse(null);
        boolean duplicate = profile == null
                ? profileRepository.existsByRegistrationNumber(registration)
                : profileRepository.existsByRegistrationNumberAndIdNot(registration, profile.getId());
        if (duplicate) {
            log.info("Student profile update rejected userId={} reason=registration_conflict", currentUserId);
            throw new DuplicateRegistrationNumberException();
        }

        String fullName = normalizedText(request.fullName());
        String faculty = normalizedText(request.faculty());
        String department = normalizedText(request.department());
        String programme = normalizedText(request.degreeProgramme());
        String phone = request.phone() == null || request.phone().isBlank()
                ? null : normalizedText(request.phone());

        student.updateFullName(fullName);
        if (profile == null) {
            profile = new StudentProfile(
                    student, registration, phone, faculty, department, programme, request.academicYear()
            );
        } else {
            profile.updateDetails(registration, phone, faculty, department, programme, request.academicYear());
        }
        // Force unique-key violations before returning; the shared exception handler
        // maps uq_student_profiles_registration to the stable 409 error code.
        StudentProfile saved = profileRepository.saveAndFlush(profile);
        userRepository.flush();
        log.info("Student profile saved userId={} profileId={}", student.getId(), saved.getId());
        return toResponse(student, saved);
    }

    /** Mandatory server-side prerequisite for Issue 11 request creation; never trust UI state. */
    @Transactional(readOnly = true)
    public void requireCompleteForSubmission(long currentUserId) {
        User student = requireStudent(currentUserId);
        StudentProfile profile = profileRepository.findByUserId(student.getId()).orElse(null);
        if (!isComplete(student, profile)) {
            throw new ProfileIncompleteException();
        }
    }

    private User requireStudent(long userId) {
        User user = userRepository.findById(userId).orElseThrow(SessionRevokedException::new);
        if (user.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students may access their own academic profile.");
        }
        return user;
    }

    private static StudentProfileResponse toResponse(User student, StudentProfile profile) {
        if (profile == null) {
            return new StudentProfileResponse(
                    student.getId(), student.getEmail(), student.getFullName(),
                    null, null, null, null, null, null, false, null
            );
        }
        return new StudentProfileResponse(
                student.getId(), student.getEmail(), student.getFullName(),
                profile.getRegistrationNumber(), profile.getPhone(), profile.getFaculty(),
                profile.getDepartment(), profile.getDegreeProgramme(), profile.getAcademicYear(),
                isComplete(student, profile), profile.getUpdatedAt()
        );
    }

    private static boolean isComplete(User student, StudentProfile profile) {
        return profile != null
                && hasText(student.getFullName(), 2, 150)
                && hasText(profile.getRegistrationNumber(), 2, 50)
                && profile.getRegistrationNumber().matches("[A-Za-z0-9/-]+")
                && hasText(profile.getFaculty(), 2, 100)
                && hasText(profile.getDepartment(), 2, 100)
                && hasText(profile.getDegreeProgramme(), 2, 150)
                && profile.getAcademicYear() >= 1 && profile.getAcademicYear() <= 8;
    }

    private static boolean hasText(String value, int min, int max) {
        return value != null && !value.isBlank()
                && value.trim().length() >= min && value.trim().length() <= max;
    }

    private static String normalizedText(String value) {
        return value.strip().replaceAll("(?U)\\s+", " ");
    }
}
