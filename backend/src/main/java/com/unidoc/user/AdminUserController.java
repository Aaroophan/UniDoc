package com.unidoc.user;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;
    private final CurrentUserProvider currentUser;

    public AdminUserController(UserService userService, CurrentUserProvider currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<UserSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return userService.listUsers(currentUser.currentUserId(), q, role, status, page, size, sort, direction);
    }

    @PostMapping
    public ResponseEntity<UserSummary> create(@Valid @RequestBody CreateManagedAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createManagedAccount(currentUser.currentUserId(), request));
    }

    @PatchMapping("/{userId}/status")
    public UserSummary status(@PathVariable @Positive long userId,
                              @Valid @RequestBody ChangeAccountStatusRequest request) {
        return userService.changeStatus(currentUser.currentUserId(), userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable @Positive long userId,
                                              @Valid @RequestBody ResetManagedPasswordRequest request) {
        userService.resetPassword(currentUser.currentUserId(), userId, request);
        return ResponseEntity.noContent().build();
    }
}
