package com.unidoc.profile;

import com.unidoc.auth.JwtTokenService;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-profile-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa",
        "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/profile-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class StudentProfileHttpTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StudentProfileRepository profiles;
    @Autowired private JwtTokenService tokens;

    @Test
    void firstTimeGetReturns200WithNullAcademicFieldsAndNoSensitiveData() throws Exception {
        User student = createUser(UserRole.STUDENT);
        mvc.perform(get("/api/student/profile").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getId()))
                .andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.registrationNumber").value(nullValue()))
                .andExpect(jsonPath("$.academicYear").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.authVersion").doesNotExist());
    }

    @Test
    void putCreatesAndReplacesProfileAndUpdatesAuthMeWithoutRevokingToken() throws Exception {
        User student = createUser(UserRole.STUDENT);
        String jwt = bearer(student);
        String first = registration();
        mvc.perform(put("/api/student/profile")
                        .header("Authorization", jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("  New    Name ", first.toLowerCase(), 1, "+94 77 123 4567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.registrationNumber").value(first))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mvc.perform(get("/api/auth/me").header("Authorization", jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"));

        String second = registration();
        mvc.perform(put("/api/student/profile")
                        .header("Authorization", jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Updated Name", second, 8, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value(second))
                .andExpect(jsonPath("$.phone").value(nullValue()))
                .andExpect(jsonPath("$.academicYear").value(8))
                .andExpect(jsonPath("$.email").value(student.getEmail()));
        assertThat(profiles.findByUserId(student.getId())).isPresent();
    }

    @Test
    void rejectsUnknownIdentityFieldsWithoutMutatingTheStudentAccount() throws Exception {
        User student = createUser(UserRole.STUDENT);
        mvc.perform(put("/api/student/profile")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Student Name", registration(), 3, null).replace("\"academicYear\":3",
                                "\"academicYear\":3,\"userId\":9999,\"email\":\"attacker@example.test\",\"role\":\"ADMIN\",\"status\":\"INACTIVE\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getId()))
                .andExpect(jsonPath("$.email").value(student.getEmail()));
        assertThat(users.findById(student.getId()).orElseThrow().getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(users.findById(student.getId()).orElseThrow().getEmail()).isEqualTo(student.getEmail());
    }

    @Test
    void validationErrorsUseFieldErrorsAndRequestId() throws Exception {
        User student = createUser(UserRole.STUDENT);
        mvc.perform(put("/api/student/profile")
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("X", "NO SPACES", 9, "INVALID-PHONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.registrationNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.academicYear").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void registrationNumberConflictReturns409WithNoOwnerInformation() throws Exception {
        User a = createUser(UserRole.STUDENT);
        User b = createUser(UserRole.STUDENT);
        String number = registration();
        mvc.perform(put("/api/student/profile").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(json("Student Alpha", number, 2, null)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/student/profile").header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON).content(json("Student Beta", number.toLowerCase(), 3, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NUMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").value(nullValue()));
        assertThat(profiles.findByUserId(b.getId())).isEmpty();
    }

    @Test
    void anonymousStaffAndAdminCannotReadOrChangeStudentProfile() throws Exception {
        mvc.perform(get("/api/student/profile")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(put("/api/student/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Some Name", registration(), 2, null)))
                .andExpect(status().isUnauthorized());
        for (UserRole role : new UserRole[] {UserRole.STAFF, UserRole.ADMIN}) {
            User notStudent = createUser(role);
            mvc.perform(get("/api/student/profile").header("Authorization", bearer(notStudent)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(put("/api/student/profile").header("Authorization", bearer(notStudent))
                            .contentType(MediaType.APPLICATION_JSON).content(json("Some Name", registration(), 2, null)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void studentsCannotChooseWhoseProfileToEdit() throws Exception {
        User a = createUser(UserRole.STUDENT);
        User b = createUser(UserRole.STUDENT);
        mvc.perform(put("/api/student/profile").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Student A", registration(), 3, null)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/student/profile").header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Student B", registration(), 4, null)))
                .andExpect(status().isOk());
        assertThat(profiles.count()).isEqualTo(2);
        assertThat(profiles.findByUserId(a.getId()).orElseThrow().getUser().getId()).isEqualTo(a.getId());
        assertThat(profiles.findByUserId(b.getId()).orElseThrow().getUser().getId()).isEqualTo(b.getId());
    }

    private User createUser(UserRole role) {
        return users.saveAndFlush(new User("http-profile-" + UUID.randomUUID() + "@example.test", "hashed", "Test Student", role));
    }

    private String bearer(User user) {
        return "Bearer " + tokens.issue(user).value();
    }

    private static String registration() {
        return "STU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String json(String name, String number, int year, String phone) {
        return """
                {"fullName":"%s", "registrationNumber":"%s", "phone":%s,
                 "faculty":" Faculty of Science ", "department":" Computing ",
                 "degreeProgramme":"BSc Computer Science", "academicYear":%s}
                """.formatted(name, number, phone == null ? "null" : "\"" + phone + "\"", year);
    }
}
