package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.file.FileValidationException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real transactional review workflow, additional-file metadata, notifications and permissions. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-review-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue14-review-flow-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReviewWorkflowServiceTests {
    private static final byte[] PDF = "%PDF-1.7\nInformation response proof".getBytes(StandardCharsets.US_ASCII);

    @Autowired StaffRequestService staff;
    @Autowired DocumentRequestService students;
    @Autowired DocumentRequestRepository requests;
    @Autowired DocumentTypeRepository types;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired RequestAttachmentRepository attachments;
    @Autowired NotificationRepository notifications;

    @Test
    void informationCyclePreservesMessagesAndFilesAndNotifiesOnlyExpectedActiveRecipients() {
        User staffOne = user(UserRole.STAFF);
        User staffTwo = user(UserRole.STAFF);
        User inactive = user(UserRole.STAFF);
        inactive.changeStatus(UserStatus.INACTIVE);
        users.saveAndFlush(inactive);
        User owner = student();
        User admin = user(UserRole.ADMIN);
        var created = submit(owner, DeliveryMethod.ELECTRONIC);
        var review = staff.startReview(staffOne.getId(), created.id(), created.version());
        String question = "Please provide a clear identity-card copy for the request.";
        var asked = staff.requestInformation(staffOne.getId(), created.id(),
                new RequestInformationRequest("  " + question + "  ", review.version()));
        assertThat(asked.status()).isEqualTo(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED);
        assertThat(asked.additionalInformationMessage()).isEqualTo(question);
        assertThat(asked.availableActions()).isEmpty();
        assertThat(asked.history().getLast().note()).isEqualTo(question);
        assertThat(asked.history().getLast().changedBy().id()).isEqualTo(staffOne.getId());
        assertThat(noticeCount(owner, NotificationType.ADDITIONAL_INFORMATION_REQUIRED)).isEqualTo(1);
        assertThat(noticeCount(admin, NotificationType.ADDITIONAL_INFORMATION_REQUIRED)).isZero();

        String answer = "The additional supporting identity file is attached.";
        var response = students.submitInformationResponse(owner.getId(), created.id(),
                new InformationResponseRequest(answer, asked.version()),
                List.of(new MockMultipartFile("files", "identity.pdf", "application/pdf", PDF)));
        assertThat(response.status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(response.version()).isEqualTo(asked.version() + 1);
        assertThat(response.additionalInformationMessage()).isNull();
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().getFirst().kind()).isEqualTo(AttachmentKind.ADDITIONAL_INFORMATION);
        assertThat(response.history().getLast().note()).isEqualTo(answer);
        assertThat(response.history().getLast().previousStatus()).isEqualTo(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED);
        assertThat(response.history().getLast().newStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(response.history().getLast().changedBy().id()).isEqualTo(owner.getId());
        assertThat(response.history()).extracting(h -> h.note()).contains(question, answer);
        assertThat(students.downloadAttachment(owner.getId(), created.id(),
                response.attachments().getFirst().id()).bytes()).isEqualTo(PDF);
        assertThat(staff.downloadAttachment(staffTwo.getId(), created.id(),
                response.attachments().getFirst().id()).bytes()).isEqualTo(PDF);
        assertThat(noticeCount(owner, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED)).isEqualTo(1);
        assertThat(noticeCount(staffOne, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED)).isEqualTo(1);
        assertThat(noticeCount(staffTwo, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED)).isEqualTo(1);
        assertThat(noticeCount(inactive, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED)).isZero();
        assertThat(noticeCount(admin, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED)).isZero();
        assertThat(staff.getDetail(staffTwo.getId(), created.id()).availableActions())
                .containsExactly(RequestAction.START_REVIEW);

        // No 'one cycle only' restriction; a second response may include zero files.
        var secondReview = staff.startReview(staffTwo.getId(), created.id(), response.version());
        var secondQuestion = staff.requestInformation(staffTwo.getId(), created.id(),
                new RequestInformationRequest("Please clarify your programme name.", secondReview.version()));
        var answeredAgain = students.submitInformationResponse(owner.getId(), created.id(),
                new InformationResponseRequest("BSc Computer Science", null), List.of());
        assertThat(answeredAgain.status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(answeredAgain.attachments()).hasSize(1);
        assertThat(answeredAgain.history()).hasSize(7);
        assertThat(answeredAgain.additionalInformationMessage()).isNull();
        assertThat(noticeCount(owner, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED)).isEqualTo(2);
    }

    @Test
    void approveAndStartProcessingAreDistinctVersionedTransitionsForBothDeliveryMethods() {
        User staffUser = user(UserRole.STAFF);
        User owner = student();
        for (DeliveryMethod method : DeliveryMethod.values()) {
            var created = submit(owner, method);
            var reviewing = staff.startReview(staffUser.getId(), created.id(), created.version());
            var approved = staff.approve(staffUser.getId(), created.id(), reviewing.version());
            assertThat(approved.status()).isEqualTo(RequestStatus.APPROVED);
            assertThat(approved.version()).isEqualTo(reviewing.version() + 1);
            assertThat(approved.availableActions()).containsExactly(RequestAction.START_PROCESSING);
            assertThat(approved.history().getLast().note()).isEqualTo("Request approved.");
            assertThat(noticeCount(owner, NotificationType.REQUEST_APPROVED)).isEqualTo(method == DeliveryMethod.ELECTRONIC ? 1 : 2);
            assertThatThrownBy(() -> staff.approve(staffUser.getId(), created.id(), reviewing.version()))
                    .isInstanceOf(RequestVersionConflictException.class);
            var processing = staff.startProcessing(staffUser.getId(), created.id(), approved.version());
            assertThat(processing.status()).isEqualTo(RequestStatus.PROCESSING);
            assertThat(processing.version()).isEqualTo(approved.version() + 1);
            assertThat(processing.history().getLast().note()).isEqualTo("Processing started.");
            assertThat(processing.availableActions()).containsExactly(method == DeliveryMethod.ELECTRONIC
                    ? RequestAction.UPLOAD_ISSUED_DOCUMENT : RequestAction.READY_FOR_COLLECTION);
            assertThatThrownBy(() -> staff.startProcessing(staffUser.getId(), created.id(), processing.version()))
                    .isInstanceOf(InvalidRequestTransitionException.class);
        }
        assertThat(noticeCount(owner, NotificationType.PROCESSING_STARTED)).isEqualTo(2);
    }

    @Test
    void rejectionStoresRequiredReasonAndRemainsTerminal() {
        User staffUser = user(UserRole.STAFF);
        User owner = student();
        var created = submit(owner, DeliveryMethod.PHYSICAL);
        var reviewing = staff.startReview(staffUser.getId(), created.id(), created.version());
        var rejected = staff.reject(staffUser.getId(), created.id(),
                new RejectRequest("  Student identification was not provided.  ", reviewing.version()));
        assertThat(rejected.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Student identification was not provided.");
        assertThat(rejected.history().getLast().note()).isEqualTo(rejected.rejectionReason());
        assertThat(rejected.availableActions()).isEmpty();
        assertThat(students.getDetail(owner.getId(), created.id()).rejectionReason())
                .isEqualTo("Student identification was not provided.");
        assertThat(noticeCount(owner, NotificationType.REQUEST_REJECTED)).isEqualTo(1);
        assertThatThrownBy(() -> staff.approve(staffUser.getId(), created.id(), rejected.version()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThatThrownBy(() -> staff.startProcessing(staffUser.getId(), created.id(), rejected.version()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThatThrownBy(() -> students.submitInformationResponse(owner.getId(), created.id(),
                new InformationResponseRequest("Please reconsider", rejected.version()), List.of()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(staff.getDetail(staffUser.getId(), created.id()).history()).hasSize(3);
    }

    @Test
    void invalidTextFilesVersionOwnershipAndCallerRoleCannotChangeState() {
        User staffUser = user(UserRole.STAFF);
        User owner = student();
        User otherStudent = student();
        User admin = user(UserRole.ADMIN);
        var created = submit(owner, DeliveryMethod.ELECTRONIC);
        var review = staff.startReview(staffUser.getId(), created.id(), created.version());
        int originalHistory = review.history().size();
        for (String bad : List.of("", "   ", "no", "x".repeat(2001), " x ")) {
            assertThatThrownBy(() -> staff.requestInformation(staffUser.getId(), created.id(),
                    new RequestInformationRequest(bad, review.version())))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> staff.reject(staffUser.getId(), created.id(),
                    new RejectRequest(bad, review.version())))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThatThrownBy(() -> staff.requestInformation(staffUser.getId(), created.id(),
                new RequestInformationRequest("Valid staff request message", review.version() - 1)))
                .isInstanceOf(RequestVersionConflictException.class);
        assertThatThrownBy(() -> staff.approve(owner.getId(), created.id(), review.version()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staff.approve(admin.getId(), created.id(), review.version()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(staff.getDetail(staffUser.getId(), created.id()).history()).hasSize(originalHistory);

        var asking = staff.requestInformation(staffUser.getId(), created.id(),
                new RequestInformationRequest("Please attach the missing document", review.version()));
        for (String bad : List.of("", " ", "a", "b".repeat(2001))) {
            assertThatThrownBy(() -> students.submitInformationResponse(owner.getId(), created.id(),
                    new InformationResponseRequest(bad, asking.version()), List.of()))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThatThrownBy(() -> students.submitInformationResponse(otherStudent.getId(), created.id(),
                new InformationResponseRequest("I own it", asking.version()), List.of()))
                .isInstanceOf(DocumentRequestNotFoundException.class);
        assertThatThrownBy(() -> students.submitInformationResponse(staffUser.getId(), created.id(),
                new InformationResponseRequest("Staff cannot respond", asking.version()), List.of()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> students.submitInformationResponse(owner.getId(), created.id(),
                new InformationResponseRequest("Actual response", asking.version() - 1), List.of()))
                .isInstanceOf(RequestVersionConflictException.class);
        assertThatThrownBy(() -> students.submitInformationResponse(owner.getId(), created.id(),
                new InformationResponseRequest("Actual response", asking.version()),
                List.of(new MockMultipartFile("files", "fake.pdf", "application/pdf", "plain text".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(FileValidationException.class);
        assertThat(staff.getDetail(staffUser.getId(), created.id()).status())
                .isEqualTo(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED);
        assertThat(staff.getDetail(staffUser.getId(), created.id()).history()).hasSize(originalHistory + 1);
    }

    @Test
    void everyUnsupportedStatusActionFailsForBothDeliveryMethods() {
        User staffUser = user(UserRole.STAFF);
        User owner = student();
        DocumentType type = type();
        for (DeliveryMethod method : DeliveryMethod.values()) {
            for (RequestStatus status : RequestStatus.values()) {
                DocumentRequest request = requests.saveAndFlush(new DocumentRequest(owner, type, method,
                        "Existing workflow state for review matrix tests", LocalDate.now().plusDays(5)));
                request.assignRequestNumber();
                request.updateStatus(status);
                request = requests.saveAndFlush(request);
                long requestId = request.getId();
                long version = request.getVersion();
                if (status != RequestStatus.SUBMITTED) {
                    assertThatThrownBy(() -> staff.startReview(staffUser.getId(), requestId, version))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                }
                if (status != RequestStatus.UNDER_REVIEW) {
                    assertThatThrownBy(() -> staff.approve(staffUser.getId(), requestId, version))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                    assertThatThrownBy(() -> staff.reject(staffUser.getId(), requestId,
                            new RejectRequest("Valid rejection reason", version)))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                    assertThatThrownBy(() -> staff.requestInformation(staffUser.getId(), requestId,
                            new RequestInformationRequest("Valid request for information", version)))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                }
                if (status != RequestStatus.APPROVED) {
                    assertThatThrownBy(() -> staff.startProcessing(staffUser.getId(), requestId, version))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                }
                if (status != RequestStatus.ADDITIONAL_INFORMATION_REQUIRED) {
                    assertThatThrownBy(() -> students.submitInformationResponse(owner.getId(), requestId,
                            new InformationResponseRequest("Valid response", version), List.of()))
                            .isInstanceOf(InvalidRequestTransitionException.class);
                }
                assertThat(history.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).isEmpty();
                assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(status);
            }
        }
    }

    private long noticeCount(User user, NotificationType type) {
        return notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, 100))
                .stream().filter(notification -> notification.getType() == type).count();
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("review-" + UUID.randomUUID() + "@example.test", "hash",
                "Review Tester " + UUID.randomUUID().toString().substring(0, 6), role));
    }

    private User student() {
        User student = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(student,
                "REV-" + UUID.randomUUID().toString().substring(0, 8), "+94770000000",
                "Faculty of Science", "Computing", "BSc Computer Science", 3));
        return student;
    }

    private DocumentType type() {
        User admin = user(UserRole.ADMIN);
        String code = "RV" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Review " + code, "Review test document requirements.",
                "Provide identification.", false, true, true, "Campus office", "Bring ID", admin));
    }

    private StudentRequestDetailResponse submit(User owner, DeliveryMethod method) {
        DocumentType type = type();
        return students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a programme application", LocalDate.now().plusDays(5), method), List.of());
    }
}
