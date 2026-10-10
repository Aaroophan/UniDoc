package com.unidoc.profile;

import com.unidoc.auth.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/profile")
public class StudentProfileController {
    private final StudentProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    public StudentProfileController(StudentProfileService profileService, CurrentUserProvider currentUserProvider) {
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public StudentProfileResponse getProfile() {
        return profileService.getCurrentProfile(currentUserProvider.currentUserId());
    }

    @PutMapping
    public StudentProfileResponse updateProfile(@Valid @RequestBody UpdateStudentProfileRequest request) {
        return profileService.updateCurrentProfile(currentUserProvider.currentUserId(), request);
    }
}
