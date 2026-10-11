package com.unidoc.notification;

import com.unidoc.common.api.PageResponse;
import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-notification-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/notification-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@RecordApplicationEvents
@Transactional
class NotificationServiceTests {
    @Autowired NotificationService service;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notes;
    @Autowired ApplicationEvents applicationEvents;

    @Test
    void createsUnreadInsideCallerTransactionAndPublishesSmallEvent() {
        User recipient = user(UserRole.STUDENT);
        NotificationResponse created = service.create(command(recipient, NotificationType.REQUEST_SUBMITTED, "/requests/123"));
        assertThat(created.id()).isPositive();
        assertThat(created.read()).isFalse();
        assertThat(created.readAt()).isNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.actionPath()).isEqualTo("/requests/123");
        assertThat(applicationEvents.stream(NotificationCreatedEvent.class))
                .anySatisfy(event -> {
                    assertThat(event.notificationId()).isEqualTo(created.id());
                    assertThat(event.recipientUserId()).isEqualTo(recipient.getId());
                    assertThat(event.type()).isEqualTo(NotificationType.REQUEST_SUBMITTED);
                });
        assertThat(service.unreadCount(recipient.getId()).count()).isEqualTo(1);
    }

    @Test
    void listIsOwnedNewestFirstAndFiltersByReadAndType() {
        User alice = user(UserRole.STUDENT);
        User bob = user(UserRole.STAFF);
        var first = service.create(command(alice, NotificationType.REQUEST_SUBMITTED, "/requests/1"));
        var second = service.create(command(alice, NotificationType.REQUEST_APPROVED, "/requests/1"));
        service.create(command(bob, NotificationType.REQUEST_REJECTED, "/staff/requests/1"));
        assertThat(service.list(alice.getId(), null, null, 0, 20).items())
                .extracting(NotificationResponse::id).containsExactly(second.id(), first.id());
        service.markRead(alice.getId(), first.id());
        assertThat(service.list(alice.getId(), true, null, 0, 20).items())
                .extracting(NotificationResponse::id).containsExactly(first.id());
        assertThat(service.list(alice.getId(), false, "REQUEST_APPROVED", 0, 20).items())
                .extracting(NotificationResponse::id).containsExactly(second.id());
        assertThat(service.unreadCount(bob.getId()).count()).isEqualTo(1);
        assertThat(service.unreadCount(alice.getId()).count()).isEqualTo(1);
    }

    @Test
    void paginationIsDatabaseBackedAndHasStableTieBreak() {
        User alice = user(UserRole.ADMIN);
        for (int i = 0; i < 5; i++) service.create(command(alice, NotificationType.REQUEST_SUBMITTED, "/requests/1"));
        PageResponse<NotificationResponse> first = service.list(alice.getId(), null, null, 0, 2);
        assertThat(first.totalItems()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items()).hasSize(2);
        assertThat(first.first()).isTrue();
        var second = service.list(alice.getId(), null, null, 1, 2);
        assertThat(second.items()).hasSize(2);
        assertThat(second.items()).extracting(NotificationResponse::id)
                .doesNotContainAnyElementsOf(first.items().stream().map(NotificationResponse::id).toList());
    }

    @Test
    void markReadIsIdempotentAndPreservesFirstReadTime() {
        User alice = user(UserRole.STUDENT);
        long id = service.create(command(alice, NotificationType.REQUEST_SUBMITTED, null)).id();
        NotificationResponse first = service.markRead(alice.getId(), id);
        assertThat(first.read()).isTrue();
        assertThat(first.readAt()).isNotNull();
        NotificationResponse repeated = service.markRead(alice.getId(), id);
        assertThat(repeated.readAt()).isEqualTo(first.readAt());
        assertThat(service.unreadCount(alice.getId()).count()).isZero();
    }

    @Test
    void markingOneOrAllCannotTouchAnotherRecipient() {
        User alice = user(UserRole.ADMIN);
        User bob = user(UserRole.STUDENT);
        long ownId = service.create(command(alice, NotificationType.REQUEST_SUBMITTED, "/notifications")).id();
        long bobId = service.create(command(bob, NotificationType.REQUEST_SUBMITTED, "/notifications")).id();
        assertThatThrownBy(() -> service.markRead(alice.getId(), bobId))
                .isInstanceOf(NotificationNotFoundException.class);
        service.markAllRead(alice.getId());
        assertThat(service.unreadCount(alice.getId()).count()).isZero();
        assertThat(service.unreadCount(bob.getId()).count()).isEqualTo(1);
        assertThat(notes.findByIdAndRecipientId(ownId, alice.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(notes.findByIdAndRecipientId(bobId, bob.getId()).orElseThrow().isRead()).isFalse();
        Instant readAt = notes.findByIdAndRecipientId(ownId, alice.getId()).orElseThrow().getReadAt();
        service.markAllRead(alice.getId());
        assertThat(notes.findByIdAndRecipientId(ownId, alice.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);
    }

    @Test
    void invalidInputsAreRejectedBeforePersistence() {
        User alice = user(UserRole.STUDENT);
        long count = notes.count();
        for (String path : new String[]{"https://malicious.example", "//external.test", "/../admin", "/%2e%2e/admin", "/%2f%2fexternal", "/path\\redirect", "/requests/1\nheader", "/requests/1#fragment"}) {
            assertThatThrownBy(() -> service.create(command(alice, NotificationType.REQUEST_SUBMITTED, path)))
                    .as("Invalid path %s", path).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.create(new NotificationCommand(alice.getId(), NotificationType.REQUEST_SUBMITTED, " ", "message", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new NotificationCommand(alice.getId(), NotificationType.REQUEST_SUBMITTED, "Title", " ", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new NotificationCommand(99999999L, NotificationType.REQUEST_SUBMITTED, "Title", "Message", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(notes.count()).isEqualTo(count);
    }

    @Test
    void badPageAndTypeFiltersReturnConsistentQueryErrors() {
        User alice = user(UserRole.STUDENT);
        assertThatThrownBy(() -> service.list(alice.getId(), null, "OTHER", 0, 20))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.list(alice.getId(), null, null, -1, 20))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.list(alice.getId(), null, null, 0, 101))
                .isInstanceOf(InvalidQueryParameterException.class);
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("notification-" + UUID.randomUUID() + "@example.test", "hash", "Notification User", role));
    }

    private NotificationCommand command(User user, NotificationType type, String path) {
        return new NotificationCommand(user.getId(), type, "A request update", "A new status is available in UniDoc.", path);
    }
}
