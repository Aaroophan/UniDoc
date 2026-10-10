package com.unidoc.auth;

import com.unidoc.common.api.ApiErrorCode;
import com.unidoc.common.web.ApiErrorWriter;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveUserFilterTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApiErrorWriter apiErrorWriter = mock(ApiErrorWriter.class);
    private final ActiveUserFilter filter = new ActiveUserFilter(userRepository, apiErrorWriter);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeMatchingUserContinuesWithSafeCurrentPrincipal() throws Exception {
        User user = mockUser(42L, UserRole.STUDENT, UserStatus.ACTIVE, 3);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        setJwtAuthentication(jwt("42", UserRole.STUDENT, 3));
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request("/api/student/profile"), new MockHttpServletResponse(), markInvoked(invoked));

        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new CurrentUserPrincipal(42L, UserRole.STUDENT, "student@example.test", UserStatus.ACTIVE, 3));
        verify(apiErrorWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void missingStoredUserReturnsSessionRevoked() throws Exception {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());
        setJwtAuthentication(jwt("42", UserRole.STUDENT, 0));
        MockHttpServletRequest request = request("/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, markInvoked(new AtomicBoolean()));

        verify(apiErrorWriter).write(
                eq(request), eq(response), eq(HttpStatus.UNAUTHORIZED), eq(ApiErrorCode.SESSION_REVOKED), any()
        );
    }

    @Test
    void inactiveStoredUserReturnsAccountInactive() throws Exception {
        User user = mockUser(42L, UserRole.STUDENT, UserStatus.INACTIVE, 0);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        setJwtAuthentication(jwt("42", UserRole.STUDENT, 0));
        MockHttpServletRequest request = request("/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, markInvoked(new AtomicBoolean()));

        verify(apiErrorWriter).write(
                eq(request), eq(response), eq(HttpStatus.FORBIDDEN), eq(ApiErrorCode.ACCOUNT_INACTIVE), any()
        );
    }

    @Test
    void staleAuthVersionReturnsSessionRevoked() throws Exception {
        User user = mockUser(42L, UserRole.STUDENT, UserStatus.ACTIVE, 2);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        setJwtAuthentication(jwt("42", UserRole.STUDENT, 1));
        MockHttpServletRequest request = request("/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, markInvoked(new AtomicBoolean()));

        verify(apiErrorWriter).write(
                eq(request), eq(response), eq(HttpStatus.UNAUTHORIZED), eq(ApiErrorCode.SESSION_REVOKED), any()
        );
    }

    @Test
    void roleMismatchReturnsSessionRevoked() throws Exception {
        User user = mockUser(42L, UserRole.STUDENT, UserStatus.ACTIVE, 0);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        setJwtAuthentication(jwt("42", UserRole.ADMIN, 0));
        MockHttpServletRequest request = request("/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, markInvoked(new AtomicBoolean()));

        verify(apiErrorWriter).write(
                eq(request), eq(response), eq(HttpStatus.UNAUTHORIZED), eq(ApiErrorCode.SESSION_REVOKED), any()
        );
    }

    @Test
    void malformedSubjectReturnsSessionRevokedWithoutRepositoryLookup() throws Exception {
        setJwtAuthentication(jwt("not-a-number", UserRole.STUDENT, 0));
        MockHttpServletRequest request = request("/api/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, markInvoked(new AtomicBoolean()));

        verify(apiErrorWriter).write(
                eq(request), eq(response), eq(HttpStatus.UNAUTHORIZED), eq(ApiErrorCode.SESSION_REVOKED), any()
        );
        verify(userRepository, never()).findById(any());
    }

    @Test
    void publicRequestWithoutBearerAuthenticationBypassesRevalidation() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request("/api/auth/login"), new MockHttpServletResponse(), markInvoked(invoked));

        assertThat(invoked).isTrue();
        verify(userRepository, never()).findById(any());
        verify(apiErrorWriter, never()).write(any(), any(), any(), any(), any());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static FilterChain markInvoked(AtomicBoolean invoked) {
        return (request, response) -> invoked.set(true);
    }

    private static Jwt jwt(String subject, UserRole role, int authVersion) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .issuer("unidoc")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", "student@example.test")
                .claim("role", role.name())
                .claim("authVersion", authVersion)
                .build();
    }

    private static void setJwtAuthentication(Jwt jwt) {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))),
                jwt.getSubject()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static User mockUser(long id, UserRole role, UserStatus status, int authVersion) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("student@example.test");
        when(user.getRole()).thenReturn(role);
        when(user.getStatus()).thenReturn(status);
        when(user.getAuthVersion()).thenReturn(authVersion);
        return user;
    }
}
