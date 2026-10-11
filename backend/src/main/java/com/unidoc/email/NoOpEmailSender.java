package com.unidoc.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Email is opt-in; domain notifications still work with SMTP disabled. */
@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailSender implements NotificationEmailSender {
    @Override
    public void send(NotificationEmailPayload notification) {
        // Deliberately no side effects, including logging notification contents.
    }
}
