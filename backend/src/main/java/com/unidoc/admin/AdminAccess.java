package com.unidoc.admin;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Defense in depth: administrator read APIs never inherit staff workflow privileges. */
@Component
public class AdminAccess {
    private final UserRepository users;

    public AdminAccess(UserRepository users) { this.users = users; }

    public User requireActiveAdministrator(long userId) {
        User user = users.findById(userId).orElseThrow(SessionRevokedException::new);
        if (user.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator privileges required.");
        }
        return user;
    }
}
