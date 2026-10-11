package com.unidoc.email;

import com.unidoc.notification.NotificationType;

/** Minimal post-commit projection. No attachment, JWT, or internal storage path. */
public record NotificationEmailPayload(
        long notificationId,
        long recipientUserId,
        NotificationType type,
        String recipientEmail,
        String title,
        String message,
        String actionPath
) {
}
