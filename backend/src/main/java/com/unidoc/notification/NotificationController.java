package com.unidoc.notification;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final CurrentUserProvider currentUser;

    public NotificationController(NotificationService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) Boolean read,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return service.list(currentUser.currentUserId(), read, type, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount() {
        return service.unreadCount(currentUser.currentUserId());
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable @Positive long notificationId) {
        return service.markRead(currentUser.currentUserId(), notificationId);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead(currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
