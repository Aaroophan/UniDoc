package com.unidoc.auth;

import com.unidoc.common.api.UserSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final CurrentUserProvider currentUserProvider;

    public AuthenticationController(
            AuthenticationService authenticationService,
            CurrentUserProvider currentUserProvider
    ) {
        this.authenticationService = authenticationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/register")
    ResponseEntity<UserSummary> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.registerStudent(request));
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @GetMapping("/me")
    UserSummary me() {
        return authenticationService.currentAccount(currentUserProvider.requireCurrentUser());
    }
}
