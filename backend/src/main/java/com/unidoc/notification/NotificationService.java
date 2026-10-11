package com.unidoc.notification;

import com.unidoc.common.api.PageResponse;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserRepository users,
                               ApplicationEventPublisher publisher, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Must join the caller's workflow transaction. Never use REQUIRES_NEW here:
     * a failed request/status change must not leave a notification or email.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationResponse create(NotificationCommand command) {
        Objects.requireNonNull(command, "Notification command is required.");
        if (command.recipientUserId() <= 0 || command.type() == null) {
            throw new IllegalArgumentException("A valid recipient and type are required.");
        }
        String title = text(command.title(), 150, "title");
        if (title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Notification title must be one line.");
        }
        String message = text(command.message(), 4000, "message");
        String path = safeActionPath(command.actionPath());
        User recipient = users.findById(command.recipientUserId())
                .orElseThrow(() -> new IllegalArgumentException("The notification recipient does not exist."));
        Notification saved = notifications.saveAndFlush(new Notification(recipient, command.type(), title, message, path));
        publisher.publishEvent(new NotificationCreatedEvent(saved.getId(), recipient.getId(), saved.getType()));
        return NotificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(long currentUserId, Boolean read,
                                                   String rawType, Integer page, Integer size) {
        ensureUserId(currentUserId);
        NotificationType type = QueryParameters.enumValue(rawType, NotificationType.class, "type");
        PageRequest pageable = PageRequest.of(QueryParameters.page(page), QueryParameters.size(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Specification<Notification> matching = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("recipient").get("id"), currentUserId));
            if (read != null) predicates.add(builder.equal(root.get("read"), read));
            if (type != null) predicates.add(builder.equal(root.get("type"), type));
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Notification> results = notifications.findAll(matching, pageable);
        return PageResponse.from(results, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(long currentUserId) {
        ensureUserId(currentUserId);
        return new UnreadNotificationCountResponse(notifications.countByRecipientIdAndReadFalse(currentUserId));
    }

    @Transactional
    public NotificationResponse markRead(long currentUserId, long notificationId) {
        ensureUserId(currentUserId);
        if (notificationId <= 0) throw new NotificationNotFoundException();
        Notification notification = notifications.findByIdAndRecipientId(notificationId, currentUserId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead(Instant.now(clock));
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(long currentUserId) {
        ensureUserId(currentUserId);
        notifications.markAllUnreadAsRead(currentUserId, Instant.now(clock));
    }

    private static void ensureUserId(long id) {
        if (id <= 0) throw new IllegalArgumentException("A valid current user is required.");
    }

    private static String text(String value, int maximum, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification " + field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maximum || normalized.chars().anyMatch(c -> c == 0 || c == 127)) {
            throw new IllegalArgumentException("Notification " + field + " is invalid.");
        }
        return normalized;
    }

    /** Allow only frontend-relative navigation, never scheme-relative/external/control URLs. */
    public static String safeActionPath(String path) {
        if (path == null || path.isBlank()) return null;
        String candidate = path.strip();
        if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.length() > 255
                || candidate.indexOf('\\') >= 0 || candidate.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Notification action path must be an internal application path.");
        }
        try {
            URI uri = URI.create(candidate);
            String decodedPath = uri.getPath();
            String decodedQuery = uri.getQuery();
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                    || !candidate.equals(uri.toString()) || uri.getRawPath() == null
                    || !uri.getRawPath().startsWith("/") || uri.getRawPath().startsWith("//")
                    || !uri.getRawPath().equals(uri.normalize().getRawPath())
                    || decodedPath == null || decodedPath.startsWith("//")
                    || decodedPath.indexOf('\\') >= 0
                    || decodedPath.chars().anyMatch(Character::isISOControl)
                    || (decodedQuery != null && decodedQuery.chars().anyMatch(Character::isISOControl))
                    || java.util.Arrays.stream(decodedPath.split("/"))
                            .anyMatch(segment -> segment.equals(".") || segment.equals(".."))) {
                throw new IllegalArgumentException("Invalid internal application path.");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Notification action path must be an internal application path.");
        }
        return candidate;
    }
}
