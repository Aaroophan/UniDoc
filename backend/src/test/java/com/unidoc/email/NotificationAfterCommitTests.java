package com.unidoc.email;

import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationService;
import com.unidoc.notification.NotificationType;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Real application event/transaction/async integration with a test sender, never Gmail. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-notification-commit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/notification-commit-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationAfterCommitTests {
    @Autowired NotificationService notifications;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CapturingEmailSender mailbox;

    @Test
    void committedNotificationInvokesSenderButRolledBackNotificationDoesNot() throws InterruptedException {
        User alice = users.saveAndFlush(new User("commit-test-" + UUID.randomUUID() + "@example.test",
                "test-hash", "Commit Test", UserRole.STUDENT));
        mailbox.clear();
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.executeWithoutResult(status -> notifications.create(new NotificationCommand(alice.getId(),
                NotificationType.REQUEST_SUBMITTED, "Request submitted", "Request confirmed.", "/requests/1")));

        NotificationEmailPayload committed = mailbox.next(5, TimeUnit.SECONDS);
        assertThat(committed).isNotNull();
        assertThat(committed.recipientUserId()).isEqualTo(alice.getId());
        assertThat(committed.type()).isEqualTo(NotificationType.REQUEST_SUBMITTED);
        assertThat(notifications.unreadCount(alice.getId()).count()).isEqualTo(1);

        mailbox.clear();
        template.executeWithoutResult(status -> {
            notifications.create(new NotificationCommand(alice.getId(), NotificationType.REQUEST_REJECTED,
                    "Not approved", "A decision is available.", "/requests/1"));
            status.setRollbackOnly();
        });
        assertThat(mailbox.next(400, TimeUnit.MILLISECONDS)).isNull();
        assertThat(notifications.unreadCount(alice.getId()).count()).isEqualTo(1);
    }

    @Test
    void creationWithoutWorkflowTransactionIsRefused() {
        User alice = users.saveAndFlush(new User("mandatory-test-" + UUID.randomUUID() + "@example.test",
                "test-hash", "Mandatory Test", UserRole.STUDENT));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> notifications.create(new NotificationCommand(
                alice.getId(), NotificationType.REQUEST_SUBMITTED, "Title", "Message", "/notifications")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @TestConfiguration
    static class TestEmailConfiguration {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() { return new CapturingEmailSender(); }
    }

    static class CapturingEmailSender implements NotificationEmailSender {
        private final LinkedBlockingQueue<NotificationEmailPayload> sent = new LinkedBlockingQueue<>();
        @Override public void send(NotificationEmailPayload notification) { sent.add(notification); }
        NotificationEmailPayload next(long amount, TimeUnit unit) throws InterruptedException { return sent.poll(amount, unit); }
        void clear() { sent.clear(); }
    }
}
