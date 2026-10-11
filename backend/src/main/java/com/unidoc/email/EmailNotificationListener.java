package com.unidoc.email;

import com.unidoc.notification.NotificationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** After commit only, asynchronous, non-retrying, and failure-tolerant. */
@Component
public class EmailNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);
    private final NotificationEmailLookup lookup;
    private final NotificationEmailSender sender;

    public EmailNotificationListener(NotificationEmailLookup lookup, NotificationEmailSender sender) {
        this.lookup = lookup;
        this.sender = sender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreatedEvent event) {
        try {
            var payload = lookup.load(event);
            if (payload.isEmpty()) {
                log.warn("Notification email skipped notificationId={} recipientUserId={} type={} category=missing_notification",
                        event.notificationId(), event.recipientUserId(), event.type());
                return;
            }
            sender.send(payload.get());
        } catch (Exception failure) {
            // No exception messages: SMTP providers may put usernames, addresses, or host details in them.
            log.warn("Notification email failed notificationId={} recipientUserId={} type={} category={}",
                    event.notificationId(), event.recipientUserId(), event.type(), failure.getClass().getSimpleName());
        }
    }
}
