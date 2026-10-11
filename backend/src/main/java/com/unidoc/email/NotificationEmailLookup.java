package com.unidoc.email;

import com.unidoc.notification.Notification;
import com.unidoc.notification.NotificationCreatedEvent;
import com.unidoc.notification.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Short read-only transaction; SMTP work occurs after this method returns. */
@Service
public class NotificationEmailLookup {
    private final NotificationRepository notifications;

    public NotificationEmailLookup(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationEmailPayload> load(NotificationCreatedEvent event) {
        return notifications.findById(event.notificationId()).filter(note ->
                note.getRecipient().getId() == event.recipientUserId() && note.getType() == event.type()
        ).map(this::project);
    }

    private NotificationEmailPayload project(Notification note) {
        return new NotificationEmailPayload(note.getId(), note.getRecipient().getId(), note.getType(),
                note.getRecipient().getEmail(), note.getTitle(), note.getMessage(), note.getActionPath());
    }
}
