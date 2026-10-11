package com.unidoc.email;

import com.unidoc.config.ApplicationProperties;
import com.unidoc.config.EmailProperties;
import com.unidoc.notification.NotificationCreatedEvent;
import com.unidoc.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationEmailTests {
    private static final NotificationEmailPayload PAYLOAD = new NotificationEmailPayload(
            12, 7, NotificationType.REQUEST_APPROVED, "student@example.test",
            "Request approved", "Your request has been approved.", "/requests/123");

    @Test
    void smtpSenderUsesPlainTextWithInternalAuthenticatedLinkAndFooter() {
        JavaMailSender mailer = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailer, new EmailProperties(true, "noreply@example.test"),
                new ApplicationProperties("http://localhost:5173/"), "smtp.gmail.com", 587,
                "account@example.test", "synthetic-test-only-password", true);
        sender.send(PAYLOAD);
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailer).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertThat(mail.getFrom()).isEqualTo("noreply@example.test");
        assertThat(mail.getTo()).containsExactly("student@example.test");
        assertThat(mail.getSubject()).isEqualTo("[UniDoc] Request approved");
        assertThat(mail.getText()).contains("Your request has been approved.")
                .contains("http://localhost:5173/requests/123")
                .contains("automatic message").doesNotContain("Authorization", "JWT", "attachment");
    }

    @Test
    void noActionPathLinksToAuthenticatedNotificationInbox() {
        NotificationEmailFormatter formatter = new NotificationEmailFormatter(
                new ApplicationProperties("http://localhost:5173"), "noreply@example.test");
        assertThat(formatter.format(new NotificationEmailPayload(12, 7, NotificationType.REQUEST_APPROVED,
                "student@example.test", "A title", "Message", null)).getText())
                .contains("http://localhost:5173/notifications");
    }

    @Test
    void invalidConfigurationFailsWhenEnabledWithoutEchoingSecrets() {
        JavaMailSender mailer = mock(JavaMailSender.class);
        assertThatThrownBy(() -> new SmtpEmailSender(mailer, new EmailProperties(true, ""),
                new ApplicationProperties("http://localhost:5173"), "smtp.gmail.com", 587,
                "account@example.test", "synthetic-test-only-password", true))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("synthetic-test-only-password");
        assertThatThrownBy(() -> new SmtpEmailSender(mailer, new EmailProperties(true, "noreply@example.test"),
                new ApplicationProperties("http://localhost:5173"), "smtp.gmail.com", 587,
                "account@example.test", "", true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SmtpEmailSender(mailer, new EmailProperties(true, "noreply@example.test"),
                new ApplicationProperties("http://localhost:5173"), "", 587,
                "account@example.test", "secret", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void badRecipientOrUnsafeActionPathAreRejectedWithoutEmail() {
        var formatter = new NotificationEmailFormatter(new ApplicationProperties("http://localhost:5173"), "noreply@example.test");
        assertThatThrownBy(() -> formatter.format(new NotificationEmailPayload(1, 2, NotificationType.REQUEST_SUBMITTED,
                "invalid", "Title", "Message", "/requests/1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> formatter.format(new NotificationEmailPayload(1, 2, NotificationType.REQUEST_SUBMITTED,
                "student@example.test", "Title", "Message", "https://other.example")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledSenderDoesNothing() {
        assertThatCode(() -> new NoOpEmailSender().send(PAYLOAD)).doesNotThrowAnyException();
    }

    @Test
    void listenerSwallowsSmtpFailuresAndMissingNotification() {
        NotificationEmailLookup lookup = mock(NotificationEmailLookup.class);
        NotificationEmailSender sender = mock(NotificationEmailSender.class);
        var event = new NotificationCreatedEvent(12, 7, NotificationType.REQUEST_APPROVED);
        when(lookup.load(event)).thenReturn(Optional.of(PAYLOAD));
        doThrow(new IllegalStateException("sensitive-smtp-failure-not-for-clients")).when(sender).send(PAYLOAD);
        EmailNotificationListener listener = new EmailNotificationListener(lookup, sender);
        assertThatCode(() -> listener.onCreated(event)).doesNotThrowAnyException();
        verify(sender).send(PAYLOAD);
        when(lookup.load(event)).thenReturn(Optional.empty());
        assertThatCode(() -> listener.onCreated(event)).doesNotThrowAnyException();
        verifyNoMoreInteractions(sender);
    }
}
