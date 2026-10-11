package com.unidoc.notification;

/** IDs and type only: no recipient email, content, or credentials in application events. */
public record NotificationCreatedEvent(long notificationId, long recipientUserId, NotificationType type) {
}
