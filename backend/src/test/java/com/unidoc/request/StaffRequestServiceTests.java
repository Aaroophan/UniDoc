package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.file.AttachmentNotFoundException;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-staff-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue13-staff-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StaffRequestServiceTests {
    private static final byte[] PDF = "%PDF-1.7\nstaff test".getBytes(StandardCharsets.US_ASCII);
    @Autowired private StaffRequestService staffService;
    @Autowired private DocumentRequestService studentService;
    @Autowired private DocumentRequestRepository requests;
    @Autowired private DocumentTypeRepository types;
    @Autowired private UserRepository users;
    @Autowired private StudentProfileRepository profiles;
    @Autowired private RequestAttachmentRepository attachments;
    @Autowired private RequestStatusHistoryRepository histories;
    @Autowired private NotificationRepository notifications;

    @Test
    void allActiveStaffSeeUnassignedRequestsAndFilteringMatchesEveryRequiredField() {
        User staffA = user(UserRole.STAFF);
        User staffB = user(UserRole.STAFF);
        User student = student(true);
        DocumentType type = type();
        var request = submit(student, type, DeliveryMethod.PHYSICAL, List.of());
        LocalDate submittedTodayUtc = LocalDate.now(ZoneOffset.UTC);

        String reference = request.requestNumber();
        assertThat(staffService.list(staffA.getId(), reference, null, null, null, null, null,
                null, 0, 10, null, null).items()).extracting(item -> item.id()).contains(request.id());
        assertThat(staffService.list(staffB.getId(), reference, null, null, null, null, null,
                null, 0, 10, null, null).items()).extracting(item -> item.id()).contains(request.id());
        assertThat(staffService.list(staffA.getId(), student.getFullName(), null, null, null, null, null,
                null, 0, 10, null, null).items()).extracting(item -> item.id()).contains(request.id());
        assertThat(staffService.list(staffA.getId(), student.getEmail(), null, null, null, null, null,
                null, 0, 10, null, null).items()).extracting(item -> item.id()).contains(request.id());
        assertThat(staffService.list(staffA.getId(), profiles.findByUserId(student.getId()).orElseThrow().getRegistrationNumber(),
                null, null, null, null, null, null, 0, 10, null, null).items())
                .extracting(item -> item.id()).contains(request.id());
        var combined = staffService.list(staffA.getId(), reference, "SUBMITTED", type.getId(), "PHYSICAL",
                submittedTodayUtc, submittedTodayUtc, request.requiredDate(), 0, 10, "requestNumber", "asc");
        assertThat(combined.items()).hasSize(1);
        assertThat(combined.items().getFirst().registrationNumber()).isNotNull();
        assertThat(combined.items().getFirst().documentTypeName()).isEqualTo(request.documentTypeName());
        assertThat(staffService.list(staffA.getId(), reference, "APPROVED", null, null, null, null,
                null, 0, 10, null, null).items()).isEmpty();
        assertThat(staffService.list(staffA.getId(), reference, null, type.getId(), "ELECTRONIC", null, null,
                null, 0, 10, null, null).items()).isEmpty();
        assertThat(staffService.list(staffA.getId(), reference, null, null, null, submittedTodayUtc.plusDays(1), null,
                null, 0, 10, null, null).items()).isEmpty();
        assertThat(staffService.list(staffA.getId(), reference, null, null, null, null,
                submittedTodayUtc.minusDays(1), null, 0, 10, null, null).items()).isEmpty();
        assertThat(staffService.list(staffA.getId(), reference, null, null, null, null,
                null, request.requiredDate().plusDays(1), 0, 10, null, null).items()).isEmpty();
        assertThat(staffService.list(staffA.getId(), reference, null, null, null, null,
                null, request.requiredDate(), 1, 10, null, null).items()).isEmpty();
        assertThat(staffService.list(staffA.getId(), reference, null, null, null, null,
                null, request.requiredDate(), 0, 10, "createdAt", "desc").totalItems()).isEqualTo(1L);
        // Verify real database ordering, stable pagination and total counts across multiple rows.
        submit(student, type, DeliveryMethod.PHYSICAL, List.of());
        submit(student, type, DeliveryMethod.ELECTRONIC, List.of());
        var firstPage = staffService.list(staffA.getId(), student.getEmail(), null, null, null,
                null, null, null, 0, 2, "requestNumber", "asc");
        var secondPage = staffService.list(staffA.getId(), student.getEmail(), null, null, null,
                null, null, null, 1, 2, "requestNumber", "asc");
        var descending = staffService.list(staffA.getId(), student.getEmail(), null, null, null,
                null, null, null, 0, 10, "requestNumber", "desc");
        assertThat(firstPage.totalItems()).isEqualTo(3L);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.items()).hasSize(1);
        var ascendingNumbers = java.util.stream.Stream.concat(firstPage.items().stream(),
                secondPage.items().stream()).map(com.unidoc.common.api.RequestSummary::requestNumber).toList();
        assertThat(ascendingNumbers).isSorted();
        var reversed = new java.util.ArrayList<>(ascendingNumbers);
        java.util.Collections.reverse(reversed);
        assertThat(descending.items().stream().map(com.unidoc.common.api.RequestSummary::requestNumber).toList())
                .containsExactlyElementsOf(reversed);
    }

    @Test
    void listHandlesMissingProfilesAndUsesSnapshotAfterCatalogueChange() {
        User staff = user(UserRole.STAFF);
        User student = student(false);
        DocumentType type = type();
        DocumentRequest row = requests.saveAndFlush(new DocumentRequest(student, type, DeliveryMethod.ELECTRONIC,
                "Historical request with missing profile.", LocalDate.now().plusDays(7)));
        row.assignRequestNumber();
        requests.saveAndFlush(row);
        var oldSnapshot = row.getDocumentTypeNameSnapshot();
        var admin = user(UserRole.ADMIN);
        type.replaceEditable(type.getCode(), "Updated type name", type.getDescription(), type.getRequirements(),
                false, true, true, "New desk", "Changed collection", true, admin);
        types.saveAndFlush(type);

        var listed = staffService.list(staff.getId(), row.getRequestNumber(), null, null, null,
                null, null, null, 0, 10, null, null);
        assertThat(listed.items()).hasSize(1);
        assertThat(listed.items().getFirst().registrationNumber()).isNull();
        assertThat(listed.items().getFirst().documentTypeName()).isEqualTo(oldSnapshot);
        var detail = staffService.getDetail(staff.getId(), row.getId());
        assertThat(detail.student().registrationNumber()).isNull();
        assertThat(detail.student().faculty()).isNull();
        assertThat(detail.documentTypeName()).isEqualTo(oldSnapshot);
        assertThat(detail.availableActions()).containsExactly(RequestAction.START_REVIEW);
    }

    @Test
    void startReviewIncrementsVersionAndAppendsOnlyOneHistoryEntryWithoutEmailOrStudentNotice() {
        User a = user(UserRole.STAFF);
        User b = user(UserRole.STAFF);
        User owner = student(true);
        var created = submit(owner, type(), DeliveryMethod.ELECTRONIC, List.of());
        long beforeNotices = notifications.count();
        var before = staffService.getDetail(a.getId(), created.id());
        assertThat(before.status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(before.availableActions()).containsExactly(RequestAction.START_REVIEW);
        assertThat(before.history()).hasSize(1);
        var reviewed = staffService.startReview(a.getId(), created.id(), before.version());
        assertThat(reviewed.status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(reviewed.version()).isEqualTo(before.version() + 1);
        assertThat(reviewed.availableActions()).containsExactly(
                RequestAction.REQUEST_INFORMATION, RequestAction.APPROVE, RequestAction.REJECT);
        assertThat(reviewed.history()).hasSize(2);
        var event = reviewed.history().getLast();
        assertThat(event.previousStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(event.newStatus()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(event.changedBy().id()).isEqualTo(a.getId());
        assertThat(event.note()).isEqualTo("Review started.");
        assertThat(notifications.count()).isEqualTo(beforeNotices);
        assertThatThrownBy(() -> staffService.startReview(b.getId(), created.id(), before.version()))
                .isInstanceOf(RequestVersionConflictException.class);
        assertThatThrownBy(() -> staffService.startReview(b.getId(), created.id(), reviewed.version()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(histories.findByRequestIdOrderByCreatedAtAscIdAsc(created.id())).hasSize(2);
        assertThat(staffService.getDetail(b.getId(), created.id()).status()).isEqualTo(RequestStatus.UNDER_REVIEW);
    }

    @Test
    void startReviewRejectsEveryNonSubmittedStateForBothDeliveryMethodsWithoutWritingHistory() {
        User staff = user(UserRole.STAFF);
        User owner = student(true);
        DocumentType documentType = type();
        for (DeliveryMethod method : DeliveryMethod.values()) {
            for (RequestStatus state : RequestStatus.values()) {
                if (state == RequestStatus.SUBMITTED) continue;
                DocumentRequest row = requests.saveAndFlush(new DocumentRequest(owner, documentType, method,
                        "Seed a historical request for an invalid transition test.", LocalDate.now().plusDays(4)));
                row.assignRequestNumber();
                row = requests.saveAndFlush(row);
                row.updateStatus(state); // Test fixture: seed an existing historical workflow state.
                row = requests.saveAndFlush(row);
                final long requestId = row.getId();
                final long version = row.getVersion();
                assertThatThrownBy(() -> staffService.startReview(staff.getId(), requestId, version))
                        .isInstanceOf(InvalidRequestTransitionException.class);
                assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(state);
                assertThat(histories.findByRequestIdOrderByCreatedAtAscIdAsc(requestId)).isEmpty();
            }
        }
    }

    @Test
    void invalidFilterValuesAndRoleBoundariesAreRejectedAndOpeningDetailNeverStartsReview() {
        User staff = user(UserRole.STAFF);
        User admin = user(UserRole.ADMIN);
        User student = student(true);
        User inactive = user(UserRole.STAFF);
        inactive.changeStatus(UserStatus.INACTIVE);
        users.saveAndFlush(inactive);
        var created = submit(student, type(), DeliveryMethod.PHYSICAL, List.of());
        assertThatThrownBy(() -> staffService.list(student.getId(), null, null, null,
                null, null, null, null, null, null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staffService.getDetail(admin.getId(), created.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> staffService.startReview(inactive.getId(), created.id(), created.version()))
                .isInstanceOf(com.unidoc.auth.InactiveAccountException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), null, "UNKNOWN", null,
                null, null, null, null, 0, 10, null, null)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), null, null, 0L,
                null, null, null, null, 0, 10, null, null)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), null, null, null, "MISSING",
                null, null, null, 0, 10, null, null)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), null, null, null, null,
                LocalDate.now(), LocalDate.now().minusDays(1), null, 0, 10, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), null, null, null, null,
                null, null, null, 0, 101, null, null)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), null, null, null, null,
                null, null, null, 0, 10, "student.passwordHash", null)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> staffService.list(staff.getId(), "x".repeat(121), null, null, null,
                null, null, null, 0, 10, null, null)).isInstanceOf(InvalidQueryParameterException.class);
        var observed = staffService.getDetail(staff.getId(), created.id());
        assertThat(observed.status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(observed.version()).isEqualTo(created.version());
        assertThat(observed.history()).hasSize(1);
    }

    @Test
    void allStaffCanDownloadButMismatchAndMissingBytesDoNotExposeOtherFileMetadata() throws Exception {
        User staffA = user(UserRole.STAFF);
        User staffB = user(UserRole.STAFF);
        User student = student(true);
        DocumentType type = type();
        var created = submit(student, type, DeliveryMethod.ELECTRONIC,
                List.of(new MockMultipartFile("files", "../proof.pdf", "application/pdf", PDF)));
        var second = submit(student, type, DeliveryMethod.ELECTRONIC,
                List.of(new MockMultipartFile("files", "other.pdf", "application/pdf", PDF)));
        long attachmentId = created.attachments().getFirst().id();
        for (var staff : List.of(staffA, staffB)) {
            var file = staffService.downloadAttachment(staff.getId(), created.id(), attachmentId);
            assertThat(file.bytes()).isEqualTo(PDF);
            assertThat(file.contentType()).isEqualTo("application/pdf");
        }
        assertThatThrownBy(() -> staffService.downloadAttachment(staffA.getId(), created.id(),
                second.attachments().getFirst().id())).isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> staffService.downloadAttachment(staffA.getId(), created.id(), 9999999L))
                .isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> staffService.downloadAttachment(student.getId(), created.id(), attachmentId))
                .isInstanceOf(AccessDeniedException.class);
        var stored = attachments.findById(attachmentId).orElseThrow();
        Files.delete(Path.of("target/issue13-staff-service-test-uploads").resolve(stored.getRelativePath()));
        // Missing stored bytes must remain indistinguishable from a missing attachment.
        assertThatThrownBy(() -> staffService.downloadAttachment(staffA.getId(), created.id(), attachmentId))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("staff-" + UUID.randomUUID() + "@example.test", "hash",
                "Person " + UUID.randomUUID().toString().substring(0, 8), role));
    }
    private User student(boolean includeProfile) {
        User result = user(UserRole.STUDENT);
        if (includeProfile) profiles.saveAndFlush(new StudentProfile(result,
                "STAFF-" + UUID.randomUUID().toString().substring(0, 8), "+94770000000",
                "Faculty of Science", "Computing", "BSc Computer Science", 3));
        return result;
    }
    private DocumentType type() {
        User admin = user(UserRole.ADMIN);
        String code = "ST" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Document " + code, "Staff workflow.",
                "Bring identification.", false, true, true, "Original desk", "Bring photo ID.", admin));
    }
    private StudentRequestDetailResponse submit(User student, DocumentType type, DeliveryMethod method,
                                                 List<org.springframework.web.multipart.MultipartFile> files) {
        return studentService.create(student.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a university application.", LocalDate.now().plusDays(7), method), files);
    }
}
