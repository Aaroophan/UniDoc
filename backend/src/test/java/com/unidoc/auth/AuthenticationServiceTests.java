package com.unidoc.auth;

import com.unidoc.common.api.UserSummary;
import com.unidoc.user.DuplicateEmailException;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-auth-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa",
        "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/auth-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class AuthenticationServiceTests {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private Validator validator;

    @Test
    void registersNormalizedActiveStudentAndStoresOnlyBcryptHash() {
        String email = uniqueEmail("register");
        RegisterRequest request = new RegisterRequest(
                "  Demo Student  ",
                "  " + email.toUpperCase() + "  ",
                "Demo@123",
                "Demo@123"
        );

        UserSummary response = authenticationService.registerStudent(request);
        User stored = userRepository.findByNormalizedEmail(email).orElseThrow();

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.fullName()).isEqualTo("Demo Student");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(stored.getAuthVersion()).isZero();
        assertThat(stored.getPasswordHash()).isNotEqualTo("Demo@123").matches("^\\$2[aby]\\$12\\$.*");
        assertThat(passwordEncoder.matches("Demo@123", stored.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        String email = uniqueEmail("duplicate");
        authenticationService.registerStudent(new RegisterRequest(
                "First Student", email, "Demo@123", "Demo@123"
        ));

        assertThatThrownBy(() -> authenticationService.registerStudent(new RegisterRequest(
                "Second Student", email.toUpperCase(), "Demo@123", "Demo@123"
        ))).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void registrationValidationEnforcesPasswordPolicyAndConfirmationField() {
        RegisterRequest request = new RegisterRequest(
                "D",
                "not-an-email",
                "weak",
                "different"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .contains("fullName", "email", "password", "confirmPassword");
    }

    @Test
    void passwordLengthBoundariesAreValidated() {
        String validEight = "Aa1!aaaa";
        String validSeventyTwo = "Aa1!" + "a".repeat(68);
        String tooLong = "Aa1!" + "a".repeat(69);

        assertThat(validator.validate(new RegisterRequest(
                "Demo Student", uniqueEmail("eight"), validEight, validEight
        ))).isEmpty();
        assertThat(validator.validate(new RegisterRequest(
                "Demo Student", uniqueEmail("seventy-two"), validSeventyTwo, validSeventyTwo
        ))).isEmpty();
        assertThat(validator.validate(new RegisterRequest(
                "Demo Student", uniqueEmail("too-long"), tooLong, tooLong
        ))).anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
    }

    @Test
    void validActiveLoginReturnsBearerTokenWithRequiredClaims() {
        String email = uniqueEmail("login");
        User user = userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode("Demo@123"),
                "Demo Student",
                UserRole.STUDENT,
                UserStatus.ACTIVE
        ));

        LoginResponse response = authenticationService.login(new LoginRequest(email.toUpperCase(), "Demo@123"));
        Jwt jwt = jwtDecoder.decode(response.token());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(user.getId());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("unidoc");
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(((Number) jwt.getClaim("authVersion")).intValue()).isZero();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void unknownEmailAndWrongPasswordUseSameGenericFailure() {
        String email = uniqueEmail("invalid");
        userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode("Demo@123"),
                "Demo Student",
                UserRole.STUDENT
        ));

        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(() ->
                authenticationService.login(new LoginRequest(email, "Wrong@123"))
        );
        Throwable unknownEmail = org.assertj.core.api.Assertions.catchThrowable(() ->
                authenticationService.login(new LoginRequest(uniqueEmail("missing"), "Wrong@123"))
        );

        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage())
                .isEqualTo("Email or password is incorrect.");
    }

    @Test
    void inactiveAccountCannotLogin() {
        String email = uniqueEmail("inactive");
        userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode("Demo@123"),
                "Inactive Student",
                UserRole.STUDENT,
                UserStatus.INACTIVE
        ));

        assertThatThrownBy(() -> authenticationService.login(new LoginRequest(email, "Demo@123")))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessage("This account is inactive. Contact an administrator.");
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.test";
    }
}
