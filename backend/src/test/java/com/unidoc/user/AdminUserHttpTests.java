package com.unidoc.user;

import com.unidoc.auth.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-admin-users-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/admin-users-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class AdminUserHttpTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService jwt;
    @Autowired private PasswordEncoder encoder;

    @Test
    void onlyAdminCanCallAllFourEndpoints() throws Exception {
        User target = account(UserRole.STAFF);
        String createJson = createBody(uniqueEmail(), "STAFF", "Good@12345");
        for (String token : new String[]{bearer(account(UserRole.STUDENT)), bearer(account(UserRole.STAFF))}) {
            mvc.perform(get("/api/admin/users").header("Authorization", token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(post("/api/admin/users").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(createJson))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/users/{id}/status", target.getId()).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/users/{id}/reset-password", target.getId()).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(resetBody("Different@1234", "Different@1234")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/{id}/status", target.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/users/{id}/reset-password", target.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("Different@1234", "Different@1234"))).andExpect(status().isUnauthorized());
    }

    @Test
    void createsBothManagedRolesAndNeverExposesPasswordsOrVersions() throws Exception {
        User admin = account(UserRole.ADMIN);
        for (String role : new String[]{"STAFF", "ADMIN"}) {
            mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(createBody(uniqueEmail(), role, "Good@12345")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value(role))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.authVersion").doesNotExist());
        }
    }

    @Test
    void rejectsStudentRoleInvalidPasswordsAndMismatchedConfirmation() throws Exception {
        User admin = account(UserRole.ADMIN);
        String token = bearer(admin);
        mvc.perform(post("/api/admin/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uniqueEmail(), "STUDENT", "Good@12345")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
        mvc.perform(post("/api/admin/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uniqueEmail(), "STAFF", "password")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(post("/api/admin/users").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Someone Name\",\"email\":\"invalid\",\"role\":\"ADMIN\",\"password\":\"Good@12345\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        mvc.perform(post("/api/admin/users/{id}/reset-password", admin.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(resetBody("Different@1234", "Mismatch@1234")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.confirmPassword").exists());
    }

    @Test
    void duplicateEmailUsesConflictAndPublicSafeEnvelope() throws Exception {
        User admin = account(UserRole.ADMIN);
        String email = uniqueEmail();
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(email, "STAFF", "Good@12345")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(email.toUpperCase(), "ADMIN", "Good@12345")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").exists()).andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void statusIsIdempotentAndOldJwtRemainsRevokedAfterReactivation() throws Exception {
        User admin = account(UserRole.ADMIN);
        User staff = account(UserRole.STAFF);
        String oldToken = bearer(staff);
        String adminToken = bearer(admin);
        mvc.perform(patch("/api/admin/users/{id}/status", staff.getId()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.authVersion").doesNotExist());
        mvc.perform(get("/api/auth/me").header("Authorization", oldToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        int rev = users.findById(staff.getId()).orElseThrow().getAuthVersion();
        mvc.perform(patch("/api/admin/users/{id}/status", staff.getId()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        assertThat(users.findById(staff.getId()).orElseThrow().getAuthVersion()).isEqualTo(rev);
        mvc.perform(patch("/api/admin/users/{id}/status", staff.getId()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/auth/me").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void rejectsSelfDeactivationAndMissingUsers() throws Exception {
        User admin = account(UserRole.ADMIN);
        mvc.perform(patch("/api/admin/users/{id}/status", admin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DEACTIVATION_NOT_ALLOWED"));
        assertThat(users.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        mvc.perform(post("/api/admin/users/9999999/reset-password").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(resetBody("Different@1234", "Different@1234")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void passwordResetReturns204RevokesOldTokenAndDoesNotReactivate() throws Exception {
        User admin = account(UserRole.ADMIN);
        User staff = account(UserRole.STAFF);
        String old = bearer(staff);
        mvc.perform(post("/api/admin/users/{id}/reset-password", staff.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(resetBody("Different@1234", "Different@1234")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(encoder.matches("Different@1234", users.findById(staff.getId()).orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(get("/api/auth/me").header("Authorization", old))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + staff.getEmail() + "\",\"password\":\"Good@12345\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + staff.getEmail() + "\",\"password\":\"Different@1234\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/users/{id}/status", staff.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/{id}/reset-password", staff.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(resetBody("Third@123456", "Third@123456")))
                .andExpect(status().isNoContent());
        assertThat(users.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void filterSortAndPagingReturnServerMetadataAndRejectInvalidValues() throws Exception {
        User admin = account(UserRole.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        users.saveAndFlush(new User("search-" + suffix + "@example.test", "hash", "Marker " + suffix, UserRole.STAFF));
        String token = bearer(admin);
        mvc.perform(get("/api/admin/users").header("Authorization", token)
                        .param("q", suffix).param("role", "STAFF").param("status", "ACTIVE")
                        .param("sort", "fullName").param("direction", "asc").param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].role").value("STAFF"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.first").value(true));
        for (String value : new String[]{"authVersion", "passwordHash", "notAllowed"}) {
            mvc.perform(get("/api/admin/users").header("Authorization", token).param("sort", value))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        mvc.perform(get("/api/admin/users").header("Authorization", token).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/admin/users").header("Authorization", token).param("role", "OTHER"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/admin/users").header("Authorization", token).param("q", "A".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(delete("/api/admin/users/{id}", admin.getId()).header("Authorization", token))
                .andExpect(status().is4xxClientError());
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User(uniqueEmail(), encoder.encode("Good@12345"), "Issue Seven Account", role));
    }

    private String bearer(User user) { return "Bearer " + jwt.issue(user).value(); }
    private static String uniqueEmail() { return "issue7-http-" + UUID.randomUUID() + "@example.test"; }
    private static String createBody(String email, String role, String password) {
        return "{\"fullName\":\"Managed User\",\"email\":\"" + email + "\",\"role\":\"" + role + "\",\"password\":\"" + password + "\"}";
    }
    private static String resetBody(String password, String confirm) {
        return "{\"newPassword\":\"" + password + "\",\"confirmPassword\":\"" + confirm + "\"}";
    }
}
