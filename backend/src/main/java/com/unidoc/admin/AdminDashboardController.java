package com.unidoc.admin;

import com.unidoc.auth.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService service;
    private final CurrentUserProvider currentUser;

    public AdminDashboardController(AdminDashboardService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AdminDashboardResponse get() { return service.dashboard(currentUser.currentUserId()); }
}
