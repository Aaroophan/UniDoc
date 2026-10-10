package com.unidoc.auth;

import com.unidoc.config.JwtProperties;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-auth-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa",
        "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/auth-http-test-uploads",
        "UNIDOC_ALLOWED_ORIGINS=http://localhost:5173",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(AuthenticationSecurityHttpTests.SecurityTestController.class)
@Transactional
class AuthenticationSecurityHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void publicRegistrationCreatesOnlyStudentAndReturnsNoPasswordOrToken() throws Exception {
        String email = uniqueEmail("public-register");
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Public Student",
                                  "email":"%s",
                                  "password":"Demo@123",
                                  "confirmPassword":"Demo@123",
                                  "role":"ADMIN",
                                  "status":"INACTIVE",
                                  "authVersion":999
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("Demo@123");
        assertThat(userRepository.findByNormalizedEmail(email).orElseThrow().getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void registrationValidationAndDuplicateEmailUseSharedErrorContract() throws Exception {
        String email = uniqueEmail("duplicate-http");
        register(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Another Student","email":"%s","password":"Demo@123","confirmPassword":"Demo@123"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Student","email":"%s","password":"Demo@123","confirmPassword":"Wrong@123"}
                                """.formatted(uniqueEmail("mismatch"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.confirmPassword").value("Password confirmation does not match."));
    }


    @Test
    void successfulLoginReturnsBearerTokenExpiryAndPublicUserSummary() throws Exception {
        String email = uniqueEmail("login-success");
        User user = createUser(email, UserRole.STAFF, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Demo@123"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.role").value("STAFF"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.authVersion").doesNotExist());
    }

    @Test
    void loginUsesGenericInvalidCredentialsAndRejectsInactiveAccount() throws Exception {
        String activeEmail = uniqueEmail("active-login");
        createUser(activeEmail, UserRole.STUDENT, UserStatus.ACTIVE);
        String inactiveEmail = uniqueEmail("inactive-login");
        createUser(inactiveEmail, UserRole.STAFF, UserStatus.INACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Wrong@123"}
                                """.formatted(activeEmail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Wrong@123"}
                                """.formatted(uniqueEmail("unknown"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Demo@123"}
                                """.formatted(inactiveEmail)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void currentAccountRequiresValidCurrentTokenAndRejectsStaleVersion() throws Exception {
        User student = createUser(uniqueEmail("me"), UserRole.STUDENT, UserStatus.ACTIVE);
        String validToken = jwtTokenService.issue(student).value();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(validToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String staleToken = customToken(student, student.getRole(), student.getAuthVersion() + 1);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(staleToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void roleRouteMatrixAndSharedRoutesAreEnforcedByBackend() throws Exception {
        User student = createUser(uniqueEmail("student-role"), UserRole.STUDENT, UserStatus.ACTIVE);
        User staff = createUser(uniqueEmail("staff-role"), UserRole.STAFF, UserStatus.ACTIVE);
        User admin = createUser(uniqueEmail("admin-role"), UserRole.ADMIN, UserStatus.ACTIVE);

        assertRoleMatrix(jwtTokenService.issue(student).value(), 200, 403, 403);
        assertRoleMatrix(jwtTokenService.issue(staff).value(), 403, 200, 403);
        assertRoleMatrix(jwtTokenService.issue(admin).value(), 403, 403, 200);
    }

    @Test
    void tokenRoleMismatchIsRevokedBeforeRouteAuthorization() throws Exception {
        User student = createUser(uniqueEmail("mismatch-role"), UserRole.STUDENT, UserStatus.ACTIVE);
        String tokenClaimingAdmin = customToken(student, UserRole.ADMIN, student.getAuthVersion());

        mockMvc.perform(get("/api/admin/security-test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenClaimingAdmin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void configuredCorsOriginGetsPreflightHeadersAndUnrelatedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,X-Request-ID"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")));

        mockMvc.perform(get("/api/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString("X-Request-ID")));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private void assertRoleMatrix(String token, int studentStatus, int staffStatus, int adminStatus) throws Exception {
        mockMvc.perform(get("/api/student/security-test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is(studentStatus));
        mockMvc.perform(get("/api/staff/security-test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is(staffStatus));
        mockMvc.perform(get("/api/admin/security-test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is(adminStatus));
        mockMvc.perform(get("/api/notifications/security-test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/document-types/security-test").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Demo Student","email":"%s","password":"Demo@123","confirmPassword":"Demo@123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private User createUser(String email, UserRole role, UserStatus status) {
        return userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode("Demo@123"),
                "Demo " + role.name(),
                role,
                status
        ));
    }

    private String customToken(User user, UserRole role, int authVersion) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.expirationSeconds()))
                .claim("email", user.getEmail())
                .claim("role", role.name())
                .claim("authVersion", authVersion)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.test";
    }

    @RestController
    public static class SecurityTestController {

        @GetMapping("/api/student/security-test")
        Map<String, String> student() {
            return Map.of("role", "student");
        }

        @GetMapping("/api/staff/security-test")
        Map<String, String> staff() {
            return Map.of("role", "staff");
        }

        @GetMapping("/api/admin/security-test")
        Map<String, String> admin() {
            return Map.of("role", "admin");
        }

        @GetMapping("/api/notifications/security-test")
        Map<String, String> notifications() {
            return Map.of("scope", "authenticated");
        }

        @GetMapping("/api/document-types/security-test")
        Map<String, String> catalogue() {
            return Map.of("scope", "authenticated");
        }
    }
}
