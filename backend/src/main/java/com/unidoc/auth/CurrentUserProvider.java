package com.unidoc.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public CurrentUserPrincipal requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentUserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user is unavailable.");
        }
        return principal;
    }

    public long currentUserId() {
        return requireCurrentUser().userId();
    }
}
