package com.unidoc.notification;

import java.time.Instant;

public record NotificationResponse(
        long id,
        NotificationType type,
        String title,
        String message,
        String actionPath,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification entity) {
        return new NotificationResponse(
                entity.getId(), entity.getType(), entity.getTitle(), entity.getMessage(),
                entity.getActionPath(), entity.isRead(), entity.getReadAt(), entity.getCreatedAt()
        );
    }
}
