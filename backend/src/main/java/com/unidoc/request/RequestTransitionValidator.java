package com.unidoc.request;

import com.unidoc.user.UserRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One authoritative, closed state/action graph shared by the later staff and delivery workflows.
 * The UI's availableActions is informational: each future command must check authorization,
 * ownership, delivery method, current status and optimistic version again in its service.
 */
@Component
public final class RequestTransitionValidator {

    public boolean canTransition(RequestStatus previous, RequestStatus next, DeliveryMethod method) {
        if (next == null || method == null) return false;
        if (previous == null) return next == RequestStatus.SUBMITTED;
        return switch (previous) {
            case SUBMITTED -> next == RequestStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> next == RequestStatus.ADDITIONAL_INFORMATION_REQUIRED
                    || next == RequestStatus.APPROVED || next == RequestStatus.REJECTED;
            case ADDITIONAL_INFORMATION_REQUIRED -> next == RequestStatus.SUBMITTED;
            case APPROVED -> next == RequestStatus.PROCESSING;
            case PROCESSING -> next == RequestStatus.READY;
            case READY -> next == RequestStatus.COMPLETED;
            case REJECTED, COMPLETED -> false;
        };
    }

    public void requireTransition(RequestStatus previous, RequestStatus next, DeliveryMethod method) {
        if (!canTransition(previous, next, method)) throw new InvalidRequestTransitionException();
    }

    public boolean canPerform(RequestAction action, UserRole role, RequestStatus status, DeliveryMethod method) {
        if (action == null || role == null || status == null || method == null) return false;
        if (role == UserRole.STUDENT) {
            return switch (action) {
                case RESPOND -> status == RequestStatus.ADDITIONAL_INFORMATION_REQUIRED;
                case DOWNLOAD_ISSUED_DOCUMENT -> method == DeliveryMethod.ELECTRONIC
                        && (status == RequestStatus.READY || status == RequestStatus.COMPLETED);
                default -> false;
            };
        }
        if (role == UserRole.STAFF) {
            return switch (action) {
                case START_REVIEW -> status == RequestStatus.SUBMITTED;
                case REQUEST_INFORMATION, APPROVE, REJECT -> status == RequestStatus.UNDER_REVIEW;
                case START_PROCESSING -> status == RequestStatus.APPROVED;
                case UPLOAD_ISSUED_DOCUMENT -> status == RequestStatus.PROCESSING
                        && method == DeliveryMethod.ELECTRONIC;
                case READY_FOR_COLLECTION -> status == RequestStatus.PROCESSING
                        && method == DeliveryMethod.PHYSICAL;
                case COMPLETE_COLLECTION -> status == RequestStatus.READY
                        && method == DeliveryMethod.PHYSICAL;
                case DOWNLOAD_ISSUED_DOCUMENT -> method == DeliveryMethod.ELECTRONIC
                        && (status == RequestStatus.READY || status == RequestStatus.COMPLETED);
                default -> false;
            };
        }
        return false; // ADMIN monitors, but cannot perform workflow or file actions.
    }

    public void requireAction(RequestAction action, UserRole role, RequestStatus status, DeliveryMethod method) {
        if (!canPerform(action, role, status, method)) throw new InvalidRequestTransitionException();
    }

    public List<RequestAction> availableActions(UserRole role, RequestStatus status, DeliveryMethod method) {
        List<RequestAction> allowed = new ArrayList<>();
        for (RequestAction action : RequestAction.values()) {
            if (canPerform(action, role, status, method)) allowed.add(action);
        }
        return List.copyOf(allowed);
    }
}
