package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.notification.NotificationType;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-physical-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue16-physical-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PhysicalCollectionServiceTests {
    @Autowired StaffRequestService staff;
    @Autowired DocumentRequestService students;
    @Autowired DocumentRequestRepository requests;
    @Autowired DocumentTypeRepository types;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired NotificationRepository notifications;
    @Autowired IssuedDocumentRepository issued;
    @Autowired Clock clock;

    @Test
    void physicalReadyAndCompletionPreserveSnapshotsAndNotifyOnlyOwnerWithPermanentHistory() {
        User first = user(UserRole.STAFF);
        User second = user(UserRole.STAFF);
        User owner = student();
        User anotherStudent = student();
        User admin = user(UserRole.ADMIN);
        DocumentType type = type(admin);
        var created = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for degree registration", today().plusDays(4), DeliveryMethod.PHYSICAL), List.of());
        assertThat(created.collectionLocation()).isEqualTo("Original Records Office");
        assertThat(created.collectionInstructions()).isEqualTo("Bring university identification.");
        // The catalogue can change without rewriting the request's historical collection guidance.
        type.replaceEditable(type.getCode(), type.getName(), type.getDescription(), type.getRequirements(),
                false, true, true, "Different office", "Different collection instructions", true, admin);
        types.saveAndFlush(type);

        var reviewed = staff.startReview(first.getId(), created.id(), created.version());
        var approved = staff.approve(first.getId(), created.id(), reviewed.version());
        var processing = staff.startProcessing(first.getId(), created.id(), approved.version());
        var ready = staff.readyForCollection(second.getId(), created.id(), processing.version());
        assertThat(ready.status()).isEqualTo(RequestStatus.READY);
        assertThat(ready.readyAt()).isNotNull();
        assertThat(ready.completedAt()).isNull();
        assertThat(ready.collectionDate()).isNull();
        assertThat(ready.collectionLocation()).isEqualTo("Original Records Office");
        assertThat(ready.collectionInstructions()).isEqualTo("Bring university identification.");
        assertThat(ready.version()).isEqualTo(processing.version() + 1);
        assertThat(ready.availableActions()).containsExactly(RequestAction.COMPLETE_COLLECTION);
        assertThat(ready.history().getLast().previousStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(ready.history().getLast().newStatus()).isEqualTo(RequestStatus.READY);
        assertThat(ready.history().getLast().changedBy().id()).isEqualTo(second.getId());
        assertThat(ready.history().getLast().note()).isEqualTo("Physical document ready for collection.");
        assertThat(students.getDetail(owner.getId(), created.id()).collectionLocation()).isEqualTo("Original Records Office");
        assertThat(students.getDetail(owner.getId(), created.id()).collectionInstructions()).isEqualTo("Bring university identification.");
        assertThat(students.getDetail(owner.getId(), created.id()).availableActions()).isEmpty();
        assertThat(noticeCount(owner, NotificationType.PHYSICAL_DOCUMENT_READY)).isEqualTo(1);
        assertThat(noticeCount(anotherStudent, NotificationType.PHYSICAL_DOCUMENT_READY)).isZero();
        assertThat(noticeCount(admin, NotificationType.PHYSICAL_DOCUMENT_READY)).isZero();
        assertThat(noticeCount(second, NotificationType.PHYSICAL_DOCUMENT_READY)).isZero();
        assertThat(issued.findByRequestId(created.id())).isEmpty();

        LocalDate collectionDay = today().minusDays(1);
        var completed = staff.completeCollection(first.getId(), created.id(),
                new CompleteCollectionRequest(collectionDay, "  ID checked.  ", ready.version()));
        assertThat(completed.status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(completed.collectionDate()).isEqualTo(collectionDay);
        assertThat(completed.collectionNote()).isEqualTo("ID checked.");
        // Compare persisted READY timestamps to the database's microsecond precision.
        assertThat(Duration.between(ready.readyAt(), completed.readyAt()).abs())
                .isLessThanOrEqualTo(Duration.ofNanos(1_000));
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.version()).isEqualTo(ready.version() + 1);
        assertThat(completed.availableActions()).isEmpty();
        assertThat(completed.history()).hasSize(6);
        assertThat(completed.history().getLast().previousStatus()).isEqualTo(RequestStatus.READY);
        assertThat(completed.history().getLast().newStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(completed.history().getLast().changedBy().id()).isEqualTo(first.getId());
        assertThat(completed.history().getLast().note()).isEqualTo("ID checked.");
        assertThat(students.getDetail(owner.getId(), created.id()).collectionDate()).isEqualTo(collectionDay);
        assertThat(students.getDetail(owner.getId(), created.id()).collectionNote()).isEqualTo("ID checked.");
        assertThat(issued.findByRequestId(created.id())).isEmpty();
        assertThat(noticeCount(owner, NotificationType.REQUEST_COMPLETED)).isEqualTo(1);
        var latest = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(owner.getId(), PageRequest.of(0, 100))
                .getContent().stream().filter(n -> n.getType() == NotificationType.PHYSICAL_DOCUMENT_READY
                        || n.getType() == NotificationType.REQUEST_COMPLETED).toList();
        assertThat(latest).extracting(n -> n.getType()).containsExactly(
                NotificationType.REQUEST_COMPLETED, NotificationType.PHYSICAL_DOCUMENT_READY);
        assertThat(latest).allSatisfy(n -> {
            assertThat(n.getActionPath()).isEqualTo("/requests/" + created.id());
            assertThat(n.getMessage()).doesNotContain("ID checked.", "Original Records Office");
        });
        assertThatThrownBy(() -> staff.completeCollection(second.getId(), created.id(),
                new CompleteCollectionRequest(today(), "repeat", completed.version())))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(noticeCount(owner, NotificationType.REQUEST_COMPLETED)).isEqualTo(1);
        assertThat(history.findByRequestIdOrderByCreatedAtAscIdAsc(created.id())).hasSize(6);
    }

    @Test
    void requiredAndFutureDateNotesAndVersionAreValidatedAndDoNotMutate() {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        var ready = ready(owner, reviewer);
        long originalHistory = history.findByRequestIdOrderByCreatedAtAscIdAsc(ready.id()).size();
        for (CompleteCollectionRequest bad : List.of(
                new CompleteCollectionRequest(null, "valid", ready.version()),
                new CompleteCollectionRequest(today(), "x".repeat(1001), ready.version()),
                new CompleteCollectionRequest(today(), "valid", -1L))) {
            assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), ready.id(), bad))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), ready.id(),
                new CompleteCollectionRequest(today().plusDays(1), "Invalid future date", ready.version())))
                .isInstanceOf(InvalidCollectionDateException.class);
        assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), ready.id(),
                new CompleteCollectionRequest(today(), "Valid note", ready.version() - 1)))
                .isInstanceOf(RequestVersionConflictException.class);
        assertThat(requests.findById(ready.id()).orElseThrow().getStatus()).isEqualTo(RequestStatus.READY);
        assertThat(history.findByRequestIdOrderByCreatedAtAscIdAsc(ready.id())).hasSize((int) originalHistory);
        var completed = staff.completeCollection(reviewer.getId(), ready.id(),
                new CompleteCollectionRequest(today(), "   ", ready.version()));
        assertThat(completed.collectionNote()).isNull();
        assertThat(completed.history().getLast().note()).isEqualTo("Physical document collected.");
    }

    @Test
    void methodStatusCallerAndStaleVersionChecksCannotCrossDeliveryBoundaries() {
        User reviewer = user(UserRole.STAFF);
        User second = user(UserRole.STAFF);
        User owner = student();
        User admin = user(UserRole.ADMIN);
        DocumentType type = type(admin);
        var electronicRequest = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Electronic original transcript", today().plusDays(2), DeliveryMethod.ELECTRONIC), List.of());
        var electronicReview = staff.startReview(reviewer.getId(), electronicRequest.id(), electronicRequest.version());
        var electronicApproved = staff.approve(reviewer.getId(), electronicRequest.id(), electronicReview.version());
        var electronicProcessing = staff.startProcessing(reviewer.getId(), electronicRequest.id(), electronicApproved.version());
        assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), electronicRequest.id(), electronicProcessing.version()))
                .isInstanceOf(DeliveryMethodMismatchException.class);
        assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), electronicRequest.id(),
                new CompleteCollectionRequest(today(), "No", electronicProcessing.version())))
                .isInstanceOf(DeliveryMethodMismatchException.class);
        assertThat(staff.getDetail(reviewer.getId(), electronicRequest.id()).readyAt()).isNull();

        var physical = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Physical document for registration", today().plusDays(2), DeliveryMethod.PHYSICAL), List.of());
        assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), physical.id(), physical.version()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        var review = staff.startReview(reviewer.getId(), physical.id(), physical.version());
        var approved = staff.approve(reviewer.getId(), physical.id(), review.version());
        var processing = staff.startProcessing(reviewer.getId(), physical.id(), approved.version());
        assertThatThrownBy(() -> staff.readyForCollection(admin.getId(), physical.id(), processing.version()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staff.readyForCollection(owner.getId(), physical.id(), processing.version()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), physical.id(), processing.version() - 1))
                .isInstanceOf(RequestVersionConflictException.class);
        var ready = staff.readyForCollection(second.getId(), physical.id(), processing.version());
        assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), physical.id(), processing.version()))
                .isInstanceOf(RequestVersionConflictException.class);
        assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), physical.id(), ready.version()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThatThrownBy(() -> staff.completeCollection(admin.getId(), physical.id(),
                new CompleteCollectionRequest(today(), null, ready.version())))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staff.completeCollection(owner.getId(), physical.id(),
                new CompleteCollectionRequest(today(), null, ready.version())))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), physical.id(),
                new CompleteCollectionRequest(today(), null, processing.version())))
                .isInstanceOf(RequestVersionConflictException.class);
        assertThat(staff.getDetail(second.getId(), physical.id()).status()).isEqualTo(RequestStatus.READY);
        assertThat(issued.findByRequestId(physical.id())).isEmpty();
    }

    @Test
    void allPhysicalStatusesOutsideProcessingAndReadyRejectCollectionCommands() {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        DocumentType type = type(user(UserRole.ADMIN));
        for (RequestStatus status : RequestStatus.values()) {
            DocumentRequest request = requests.saveAndFlush(new DocumentRequest(owner, type, DeliveryMethod.PHYSICAL,
                    "Existing request for action state matrix", today().plusDays(2)));
            request.assignRequestNumber();
            request.updateStatus(status);
            request = requests.saveAndFlush(request); // Use the managed merged version, not stale @Version.
            long id = request.getId();
            long version = request.getVersion();
            if (status != RequestStatus.PROCESSING) {
                assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), id, version))
                        .isInstanceOf(InvalidRequestTransitionException.class);
            }
            if (status != RequestStatus.READY) {
                assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), id,
                        new CompleteCollectionRequest(today(), null, version)))
                        .isInstanceOf(InvalidRequestTransitionException.class);
            }
        }
    }

    private StaffRequestDetailResponse ready(User owner, User reviewer) {
        DocumentType type = type(user(UserRole.ADMIN));
        var created = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Physical document to collect", today().plusDays(3), DeliveryMethod.PHYSICAL), List.of());
        var reviewed = staff.startReview(reviewer.getId(), created.id(), created.version());
        var approved = staff.approve(reviewer.getId(), created.id(), reviewed.version());
        var processing = staff.startProcessing(reviewer.getId(), created.id(), approved.version());
        return staff.readyForCollection(reviewer.getId(), created.id(), processing.version());
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.systemDefault())); }
    private long noticeCount(User user, NotificationType type) {
        return notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, 100))
                .stream().filter(n -> n.getType() == type).count();
    }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("phy-" + UUID.randomUUID() + "@example.test", "hash", "Physical Tester", role));
    }
    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "PHY-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        return owner;
    }
    private DocumentType type(User admin) {
        String code = "PHY" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Physical " + code,
                "Physical collection test document.", "Bring university identification", false, true, true,
                "Original Records Office", "Bring university identification.", admin));
    }
}
