package com.unidoc.auth;

import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.UserSummary;
import com.unidoc.common.persistence.Normalization;
import com.unidoc.user.DuplicateEmailException;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public UserSummary registerStudent(RegisterRequest request) {
        String normalizedEmail = Normalization.email(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                UserRole.STUDENT,
                UserStatus.ACTIVE
        );
        User saved = userRepository.saveAndFlush(user);
        log.info("Registration succeeded userId={}", saved.getId());
        return CommonDtoMapper.toUserSummary(saved);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = Normalization.email(request.email());
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            log.info("Login failed email={} reason=invalid_credentials", maskEmail(normalizedEmail));
            throw new InvalidCredentialsException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Login failed email={} reason=inactive_account", maskEmail(normalizedEmail));
            throw new InactiveAccountException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("Login failed email={} reason=invalid_credentials", maskEmail(normalizedEmail));
            throw new InvalidCredentialsException();
        }

        IssuedToken issued = jwtTokenService.issue(user);
        log.info("Login succeeded userId={} role={}", user.getId(), user.getRole());
        return new LoginResponse(
                issued.value(),
                "Bearer",
                issued.expiresAt(),
                CommonDtoMapper.toUserSummary(user)
        );
    }

    @Transactional(readOnly = true)
    public UserSummary currentAccount(CurrentUserPrincipal principal) {
        User user = userRepository.findById(principal.userId()).orElseThrow(SessionRevokedException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveAccountException();
        }
        if (user.getRole() != principal.role() || user.getAuthVersion() != principal.authVersion()) {
            throw new SessionRevokedException();
        }
        return CommonDtoMapper.toUserSummary(user);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
