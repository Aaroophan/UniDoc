package com.unidoc.request;

import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestTransitionValidatorTests {
    private final RequestTransitionValidator rules = new RequestTransitionValidator();

    private record Edge(RequestStatus from, RequestStatus to) { }

    static Stream<Edge> approvedEdges() {
        return Stream.of(
                new Edge(null, RequestStatus.SUBMITTED),
                new Edge(RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW),
                new Edge(RequestStatus.UNDER_REVIEW, RequestStatus.ADDITIONAL_INFORMATION_REQUIRED),
                new Edge(RequestStatus.UNDER_REVIEW, RequestStatus.APPROVED),
                new Edge(RequestStatus.UNDER_REVIEW, RequestStatus.REJECTED),
                new Edge(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED, RequestStatus.SUBMITTED),
                new Edge(RequestStatus.APPROVED, RequestStatus.PROCESSING),
                new Edge(RequestStatus.PROCESSING, RequestStatus.READY),
                new Edge(RequestStatus.READY, RequestStatus.COMPLETED)
        );
    }

    @ParameterizedTest
    @MethodSource("approvedEdges")
    void acceptsEveryDocumentedStatusEdgeForBothDeliveryMethods(Edge edge) {
        for (DeliveryMethod method : DeliveryMethod.values()) {
            assertThat(rules.canTransition(edge.from(), edge.to(), method)).isTrue();
            rules.requireTransition(edge.from(), edge.to(), method);
        }
    }

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    void rejectsEveryUnlistedEdgeFromEachStatus(RequestStatus from) {
        Set<Edge> approved = approvedEdges().collect(java.util.stream.Collectors.toSet());
        for (DeliveryMethod method : DeliveryMethod.values()) {
            for (RequestStatus to : RequestStatus.values()) {
                boolean expected = approved.contains(new Edge(from, to));
                assertThat(rules.canTransition(from, to, method)).isEqualTo(expected);
                if (!expected) {
                    assertThatThrownBy(() -> rules.requireTransition(from, to, method))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                }
            }
        }
    }

    @Test
    void cannotEnterAnyStateExceptSubmittedAtCreationOrLeaveTerminalState() {
        for (RequestStatus status : RequestStatus.values()) {
            assertThat(rules.canTransition(null, status, DeliveryMethod.ELECTRONIC))
                    .isEqualTo(status == RequestStatus.SUBMITTED);
        }
        for (RequestStatus terminal : List.of(RequestStatus.REJECTED, RequestStatus.COMPLETED)) {
            for (RequestStatus next : RequestStatus.values()) {
                assertThat(rules.canTransition(terminal, next, DeliveryMethod.PHYSICAL)).isFalse();
            }
        }
        assertThat(rules.canTransition(RequestStatus.SUBMITTED, null, DeliveryMethod.ELECTRONIC)).isFalse();
    }

    @Test
    void enumeratesExactlyThePermittedRoleAndDeliveryActions() {
        assertThat(rules.availableActions(UserRole.STAFF, RequestStatus.UNDER_REVIEW, DeliveryMethod.PHYSICAL))
                .containsExactly(RequestAction.REQUEST_INFORMATION, RequestAction.APPROVE, RequestAction.REJECT);
        assertThat(rules.availableActions(UserRole.STUDENT, RequestStatus.SUBMITTED, DeliveryMethod.ELECTRONIC))
                .isEmpty();
        assertThat(rules.availableActions(UserRole.STUDENT, RequestStatus.ADDITIONAL_INFORMATION_REQUIRED, DeliveryMethod.PHYSICAL))
                .containsExactly(RequestAction.RESPOND);
        assertThat(rules.availableActions(UserRole.STAFF, RequestStatus.PROCESSING, DeliveryMethod.ELECTRONIC))
                .containsExactly(RequestAction.UPLOAD_ISSUED_DOCUMENT);
        assertThat(rules.availableActions(UserRole.STAFF, RequestStatus.PROCESSING, DeliveryMethod.PHYSICAL))
                .containsExactly(RequestAction.READY_FOR_COLLECTION);
        assertThat(rules.availableActions(UserRole.STAFF, RequestStatus.READY, DeliveryMethod.PHYSICAL))
                .containsExactly(RequestAction.COMPLETE_COLLECTION);
        assertThat(rules.availableActions(UserRole.STUDENT, RequestStatus.READY, DeliveryMethod.ELECTRONIC))
                .containsExactly(RequestAction.DOWNLOAD_ISSUED_DOCUMENT);
        assertThat(rules.availableActions(UserRole.STUDENT, RequestStatus.COMPLETED, DeliveryMethod.PHYSICAL))
                .isEmpty();
        for (RequestStatus status : RequestStatus.values()) {
            assertThat(rules.availableActions(UserRole.ADMIN, status, DeliveryMethod.ELECTRONIC)).isEmpty();
        }
        assertThatThrownBy(() -> rules.requireAction(RequestAction.APPROVE,
                UserRole.STUDENT, RequestStatus.UNDER_REVIEW, DeliveryMethod.ELECTRONIC))
                .isInstanceOf(InvalidRequestTransitionException.class);
    }
    @Test
    void everyRoleStatusMethodActionCombinationIsClosedAndExplicit() {
        for (UserRole role : UserRole.values()) {
            for (DeliveryMethod method : DeliveryMethod.values()) {
                for (RequestStatus status : RequestStatus.values()) {
                    for (RequestAction action : RequestAction.values()) {
                        boolean permitted = switch (role) {
                            case ADMIN -> false;
                            case STUDENT -> (action == RequestAction.RESPOND
                                    && status == RequestStatus.ADDITIONAL_INFORMATION_REQUIRED)
                                    || (action == RequestAction.DOWNLOAD_ISSUED_DOCUMENT
                                    && method == DeliveryMethod.ELECTRONIC
                                    && (status == RequestStatus.READY || status == RequestStatus.COMPLETED));
                            case STAFF -> switch (action) {
                                case START_REVIEW -> status == RequestStatus.SUBMITTED;
                                case REQUEST_INFORMATION, APPROVE, REJECT -> status == RequestStatus.UNDER_REVIEW;
                                case START_PROCESSING -> status == RequestStatus.APPROVED;
                                case UPLOAD_ISSUED_DOCUMENT -> method == DeliveryMethod.ELECTRONIC && status == RequestStatus.PROCESSING;
                                case READY_FOR_COLLECTION -> method == DeliveryMethod.PHYSICAL && status == RequestStatus.PROCESSING;
                                case COMPLETE_COLLECTION -> method == DeliveryMethod.PHYSICAL && status == RequestStatus.READY;
                                case DOWNLOAD_ISSUED_DOCUMENT -> method == DeliveryMethod.ELECTRONIC
                                        && (status == RequestStatus.READY || status == RequestStatus.COMPLETED);
                                case RESPOND -> false;
                            };
                        };
                        assertThat(rules.canPerform(action, role, status, method)).isEqualTo(permitted);
                        assertThat(rules.availableActions(role, status, method).contains(action)).isEqualTo(permitted);
                        if (permitted) {
                            rules.requireAction(action, role, status, method);
                        } else {
                            assertThatThrownBy(() -> rules.requireAction(action, role, status, method))
                                    .isInstanceOf(InvalidRequestTransitionException.class);
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    void everyStaffStateAndDeliveryPairHasExactlyTheDocumentedActionSet(RequestStatus status) {
        for (DeliveryMethod method : DeliveryMethod.values()) {
            List<RequestAction> expected = switch (status) {
                case SUBMITTED -> List.of(RequestAction.START_REVIEW);
                case UNDER_REVIEW -> List.of(RequestAction.REQUEST_INFORMATION, RequestAction.APPROVE,
                        RequestAction.REJECT);
                case APPROVED -> List.of(RequestAction.START_PROCESSING);
                case PROCESSING -> method == DeliveryMethod.ELECTRONIC
                        ? List.of(RequestAction.UPLOAD_ISSUED_DOCUMENT)
                        : List.of(RequestAction.READY_FOR_COLLECTION);
                case READY -> method == DeliveryMethod.ELECTRONIC
                        ? List.of(RequestAction.DOWNLOAD_ISSUED_DOCUMENT)
                        : List.of(RequestAction.COMPLETE_COLLECTION);
                case COMPLETED -> method == DeliveryMethod.ELECTRONIC
                        ? List.of(RequestAction.DOWNLOAD_ISSUED_DOCUMENT) : List.of();
                case ADDITIONAL_INFORMATION_REQUIRED, REJECTED -> List.of();
            };
            assertThat(rules.availableActions(UserRole.STAFF, status, method)).containsExactlyElementsOf(expected);
            assertThat(rules.availableActions(UserRole.ADMIN, status, method)).isEmpty();
        }
    }

}
