package com.unidoc.auth;

import com.unidoc.common.api.ApiErrorCode;
import com.unidoc.common.web.ApiErrorWriter;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ApiErrorWriter apiErrorWriter;

    public ActiveUserFilter(UserRepository userRepository, ApiErrorWriter apiErrorWriter) {
        this.userRepository = userRepository;
        this.apiErrorWriter = apiErrorWriter;
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/health".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        long userId;
        UserRole tokenRole;
        int tokenAuthVersion;
        try {
            userId = JwtClaims.requirePositiveUserId(jwtAuthentication.getToken());
            tokenRole = JwtClaims.requireRole(jwtAuthentication.getToken());
            tokenAuthVersion = JwtClaims.requireAuthVersion(jwtAuthentication.getToken());
        } catch (IllegalArgumentException exception) {
            rejectSession(request, response);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            rejectSession(request, response);
            return;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            rejectInactive(request, response);
            return;
        }
        if (user.getRole() != tokenRole || user.getAuthVersion() != tokenAuthVersion) {
            rejectSession(request, response);
            return;
        }

        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                user.getId(),
                user.getRole(),
                user.getEmail(),
                user.getStatus(),
                user.getAuthVersion()
        );
        UsernamePasswordAuthenticationToken revalidated = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                authentication.getAuthorities()
        );
        revalidated.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(revalidated);

        filterChain.doFilter(request, response);
    }

    private void rejectSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        apiErrorWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.SESSION_REVOKED,
                "Your session is no longer valid. Please sign in again."
        );
    }

    private void rejectInactive(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        apiErrorWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_INACTIVE,
                "This account is inactive. Contact an administrator."
        );
    }
}
