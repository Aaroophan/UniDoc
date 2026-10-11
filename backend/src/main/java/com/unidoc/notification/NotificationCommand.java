package com.unidoc.notification;

/** Internal workflow command. Never bind this type to an HTTP endpoint. */
public record NotificationCommand(
        long recipientUserId,
        NotificationType type,
        String title,
        String message,
        String actionPath
) {
}
