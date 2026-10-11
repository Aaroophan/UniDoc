package com.unidoc.notification;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException() {
        super("The requested resource was not found.");
    }
}
