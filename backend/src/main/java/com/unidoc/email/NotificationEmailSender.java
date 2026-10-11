package com.unidoc.email;

/** Side-effect boundary. Never invoke it from inside a workflow transaction. */
public interface NotificationEmailSender {
    void send(NotificationEmailPayload notification);
}
