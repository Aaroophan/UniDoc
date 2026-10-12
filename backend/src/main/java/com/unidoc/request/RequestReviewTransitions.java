package com.unidoc.request;

import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationService;
import com.unidoc.user.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Closed review-transition writer. Called only inside explicit, transactional use cases.
 * There is deliberately no controller that accepts an arbitrary target state. The request
 * version is checked before any mutation and saveAndFlush enforces JPA optimistic locking.
 * History and notifications join the exact same caller transaction.
 */
@Component
final class RequestReviewTransitions {
    private final DocumentRequestRepository requests;
    private final RequestStatusHistoryRepository history;
    private final NotificationService notifications;
    private final RequestTransitionValidator transitions;

    RequestReviewTransitions(DocumentRequestRepository requests, RequestStatusHistoryRepository history,
                             NotificationService notifications, RequestTransitionValidator transitions) {
        this.requests = requests;
        this.history = history;
        this.notifications = notifications;
        this.transitions = transitions;
    }

    RequestStatusHistory record(DocumentRequest request, User actor, long expectedVersion,
                                RequestAction action, String note, Consumer<DocumentRequest> changes,
                                List<NotificationCommand> notices) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(changes);
        Objects.requireNonNull(notices);
        if (expectedVersion < 0 || request.getVersion() != expectedVersion) {
            throw new RequestVersionConflictException();
        }
        RequestStatus next = switch (action) {
            case START_REVIEW -> RequestStatus.UNDER_REVIEW;
            case REQUEST_INFORMATION -> RequestStatus.ADDITIONAL_INFORMATION_REQUIRED;
            case RESPOND -> RequestStatus.SUBMITTED;
            case APPROVE -> RequestStatus.APPROVED;
            case REJECT -> RequestStatus.REJECTED;
            case START_PROCESSING -> RequestStatus.PROCESSING;
            default -> throw new InvalidRequestTransitionException();
        };
        RequestStatus previous = request.getStatus();
        transitions.requireAction(action, actor.getRole(), previous, request.getDeliveryMethod());
        transitions.requireTransition(previous, next, request.getDeliveryMethod());
        changes.accept(request);
        request.updateStatus(next);
        requests.saveAndFlush(request);
        RequestStatusHistory event = history.saveAndFlush(new RequestStatusHistory(
                request, previous, next, actor, note));
        for (NotificationCommand notice : notices) notifications.create(notice);
        return event;
    }
}
