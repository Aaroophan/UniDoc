package com.unidoc.profile;

import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-profile-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa",
        "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/profile-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class StudentProfileServiceTests {

    @Autowired private StudentProfileService service;
    @Autowired private StudentProfileRepository profiles;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    @Test
    void firstLoginReturnsAccountAndNullAcademicFieldsAndFailsPrerequisite() {
        User student = createUser(UserRole.STUDENT);
        StudentProfileResponse response = service.getCurrentProfile(student.getId());

        assertThat(response.userId()).isEqualTo(student.getId());
        assertThat(response.email()).isEqualTo(student.getEmail());
        assertThat(response.fullName()).isEqualTo(student.getFullName());
        assertThat(response.complete()).isFalse();
        assertThat(response.registrationNumber()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.faculty()).isNull();
        assertThat(response.department()).isNull();
        assertThat(response.degreeProgramme()).isNull();
        assertThat(response.academicYear()).isNull();
        assertThat(response.updatedAt()).isNull();
        assertThat(profiles.findByUserId(student.getId())).isEmpty();
        assertThatThrownBy(() -> service.requireCompleteForSubmission(student.getId()))
                .isInstanceOf(ProfileIncompleteException.class);
    }

    @Test
    void createProfileNormalizesValuesAndAllowsRequestPrerequisite() {
        User student = createUser(UserRole.STUDENT);
        String registration = registration();
        StudentProfileResponse result = service.updateCurrentProfile(student.getId(), request(
                "  Jane   Example ", " " + registration.toLowerCase() + " ", "  +94   77  111 2222  ", 1
        ));

        assertThat(result.userId()).isEqualTo(student.getId());
        assertThat(result.registrationNumber()).isEqualTo(registration);
        assertThat(result.fullName()).isEqualTo("Jane Example");
        assertThat(result.phone()).isEqualTo("+94 77 111 2222");
        assertThat(result.faculty()).isEqualTo("Faculty of Science");
        assertThat(result.degreeProgramme()).isEqualTo("BSc Computer Science");
        assertThat(result.academicYear()).isEqualTo(1);
        assertThat(result.complete()).isTrue();
        assertThat(result.updatedAt()).isNotNull();
        service.requireCompleteForSubmission(student.getId());

        entityManager.clear();
        assertThat(users.findById(student.getId()).orElseThrow().getFullName()).isEqualTo("Jane Example");
        assertThat(profiles.findByUserId(student.getId())).isPresent();
    }

    @Test
    void replacingProfileChangesNameAndAcademicDetailsWithoutChangingEmailOrIdentity() {
        User student = createUser(UserRole.STUDENT);
        String originalEmail = student.getEmail();
        String reg = registration();
        service.updateCurrentProfile(student.getId(), request("First Name", reg, null, 3));
        Long profileId = profiles.findByUserId(student.getId()).orElseThrow().getId();
        String changedRegistration = registration();

        StudentProfileResponse updated = service.updateCurrentProfile(
                student.getId(), request("Second Name", changedRegistration, "  ", 8)
        );
        assertThat(updated.email()).isEqualTo(originalEmail);
        assertThat(updated.userId()).isEqualTo(student.getId());
        assertThat(updated.fullName()).isEqualTo("Second Name");
        assertThat(updated.registrationNumber()).isEqualTo(changedRegistration);
        assertThat(updated.phone()).isNull();
        assertThat(updated.academicYear()).isEqualTo(8);
        assertThat(profiles.findByUserId(student.getId()).orElseThrow().getId()).isEqualTo(profileId);
        assertThat(users.findById(student.getId()).orElseThrow().getEmail()).isEqualTo(originalEmail);
    }

    @Test
    void duplicateRegistrationIsRejectedForOtherStudentWithoutChangingAccountName() {
        User a = createUser(UserRole.STUDENT);
        User b = createUser(UserRole.STUDENT);
        String taken = registration();
        service.updateCurrentProfile(a.getId(), request("Student Alpha", taken, null, 4));
        assertThatThrownBy(() -> service.updateCurrentProfile(b.getId(), request("Hijacked Name", taken.toLowerCase(), null, 2)))
                .isInstanceOf(DuplicateRegistrationNumberException.class);
        assertThat(users.findById(b.getId()).orElseThrow().getFullName()).isEqualTo("Test Student");
        assertThat(profiles.findByUserId(b.getId())).isEmpty();
        assertThat(profiles.findByUserId(a.getId()).orElseThrow().getRegistrationNumber()).isEqualTo(taken);
    }

    @Test
    void updatingTheSameStudentWithUnchangedRegistrationIsAllowed() {
        User student = createUser(UserRole.STUDENT);
        String number = registration();
        service.updateCurrentProfile(student.getId(), request("First Name", number, null, 2));
        StudentProfileResponse repeat = service.updateCurrentProfile(student.getId(), request("Changed Name", number.toLowerCase(), null, 3));
        assertThat(repeat.registrationNumber()).isEqualTo(number);
        assertThat(repeat.fullName()).isEqualTo("Changed Name");
        assertThat(profiles.count()).isEqualTo(1);
    }

    @Test
    void invalidAcademicYearsAndRegistrationOrPhoneDoNotPersistEitherRow() {
        User student = createUser(UserRole.STUDENT);
        UpdateStudentProfileRequest[] invalid = {
                request("Changed Name", registration(), null, 0),
                request("Changed Name", registration(), null, 9),
                request("Changed Name", "INVALID NUMBER", null, 3),
                request("Changed Name", registration(), "abcdefg", 3)
        };
        for (UpdateStudentProfileRequest candidate : invalid) {
            assertThatThrownBy(() -> service.updateCurrentProfile(student.getId(), candidate))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThat(profiles.findByUserId(student.getId())).isEmpty();
        assertThat(users.findById(student.getId()).orElseThrow().getFullName()).isEqualTo("Test Student");
    }

    @Test
    void invalidUpdateCannotChangeAnExistingCompleteProfile() {
        User student = createUser(UserRole.STUDENT);
        String number = registration();
        service.updateCurrentProfile(student.getId(), request("Original Name", number, null, 5));
        assertThatThrownBy(() -> service.updateCurrentProfile(student.getId(), request("Changed Name", registration(), "123", 2)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(service.getCurrentProfile(student.getId()).fullName()).isEqualTo("Original Name");
        assertThat(service.getCurrentProfile(student.getId()).registrationNumber()).isEqualTo(number);
    }

    @Test
    void structurallyIncompleteLegacyProfileIsNotConsideredComplete() {
        User student = createUser(UserRole.STUDENT);
        StudentProfile legacy = new StudentProfile(student, registration(), null, " ", "Computing", "BSc", 2);
        profiles.saveAndFlush(legacy);
        assertThat(service.getCurrentProfile(student.getId()).complete()).isFalse();
        assertThatThrownBy(() -> service.requireCompleteForSubmission(student.getId()))
                .isInstanceOf(ProfileIncompleteException.class);
    }

    @Test
    void onlyStudentAccountsMayReadUpdateOrSatisfyProfilePrerequisite() {
        for (UserRole forbidden : new UserRole[] {UserRole.STAFF, UserRole.ADMIN}) {
            User user = createUser(forbidden);
            assertThatThrownBy(() -> service.getCurrentProfile(user.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.updateCurrentProfile(user.getId(), request("Some Name", registration(), null, 2)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.requireCompleteForSubmission(user.getId()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    private User createUser(UserRole role) {
        return users.saveAndFlush(new User("profile-" + UUID.randomUUID() + "@example.test", "not-a-real-password-hash", "Test Student", role));
    }

    private static String registration() {
        return "REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static UpdateStudentProfileRequest request(String name, String reg, String phone, Integer year) {
        return new UpdateStudentProfileRequest(name, reg, phone, "Faculty   of Science", "Department of Computing", "BSc Computer Science", year);
    }
}
