package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeInactiveException;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.file.FileValidationException;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.notification.NotificationType;
import com.unidoc.profile.ProfileIncompleteException;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-request-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue11-request-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DocumentRequestServiceTests {
    private static final byte[] PDF = "%PDF-1.7\nrequest test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10, 1};

    @Autowired private DocumentRequestService service;
    @Autowired private DocumentRequestRepository requests;
    @Autowired private DocumentTypeRepository types;
    @Autowired private UserRepository users;
    @Autowired private StudentProfileRepository profiles;
    @Autowired private RequestAttachmentRepository attachments;
    @Autowired private RequestStatusHistoryRepository histories;
    @Autowired private NotificationRepository notifications;

    @Test
    void optionalSubmissionCommitsNumberSnapshotInitialHistoryAndCorrectNotificationRecipients() {
        User student = studentWithProfile();
        User staff = user(UserRole.STAFF, UserStatus.ACTIVE);
        User inactiveStaff = user(UserRole.STAFF, UserStatus.INACTIVE);
        User admin = user(UserRole.ADMIN, UserStatus.ACTIVE);
        DocumentType type = type(false, true, true, "Original Collection Desk");
        var command = new CreateDocumentRequest(type.getId(), "Required for academic administration.",
                LocalDate.now().plusDays(5), DeliveryMethod.PHYSICAL);
        var created = service.create(student.getId(), command, null);

        assertThat(created.id()).isPositive();
        assertThat(created.requestNumber()).isEqualTo("UDR-%06d".formatted(created.id()));
        assertThat(created.status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(created.student().registrationNumber()).isNotBlank();
        assertThat(created.documentTypeName()).isEqualTo("Document " + type.getCode());
        assertThat(created.collectionLocation()).isEqualTo("Original Collection Desk");
        assertThat(created.collectionInstructions()).isEqualTo("Bring identification.");
        assertThat(created.attachments()).isEmpty();
        assertThat(created.history()).hasSize(1);
        assertThat(created.history().getFirst().previousStatus()).isNull();
        assertThat(created.history().getFirst().newStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(created.history().getFirst().changedBy().id()).isEqualTo(student.getId());
        assertThat(created.history().getFirst().note()).isEqualTo("Request submitted.");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        var confirmation = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(student.getId(),
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        assertThat(confirmation).anySatisfy(item -> {
            assertThat(item.getType()).isEqualTo(NotificationType.REQUEST_SUBMITTED);
            assertThat(item.getMessage()).contains(created.requestNumber());
            assertThat(item.getActionPath()).isEqualTo("/requests/" + created.id());
        });
        var staffNotices = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(staff.getId(),
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        assertThat(staffNotices).anySatisfy(item -> {
            assertThat(item.getType()).isEqualTo(NotificationType.REQUEST_SUBMITTED);
            assertThat(item.getActionPath()).isEqualTo("/staff/requests/" + created.id());
            assertThat(item.getMessage()).contains(created.requestNumber());
        });
        assertThat(notifications.countByRecipientIdAndReadFalse(inactiveStaff.getId())).isZero();
        assertThat(notifications.countByRecipientIdAndReadFalse(admin.getId())).isZero();

        // A later catalogue edit does not change the request's historical snapshots.
        type.replaceEditable(type.getCode(), "Renamed Document", type.getDescription(),
                type.getRequirements(), false, true, true, "New Collection Desk",
                "New instructions.", true, admin);
        types.saveAndFlush(type);
        var reloaded = service.getDetail(student.getId(), created.id());
        assertThat(reloaded.documentTypeName()).isEqualTo(created.documentTypeName());
        assertThat(reloaded.collectionLocation()).isEqualTo("Original Collection Desk");
        assertThat(reloaded.collectionInstructions()).isEqualTo("Bring identification.");
    }

    @Test
    void storingFilesPersistsImmutableRelativeMetadataAndDownloadChecksOwnerAndRequestMatch() throws Exception {
        User a = studentWithProfile();
        User b = studentWithProfile();
        DocumentType type = type(true, true, true, "Desk");
        var submission = new CreateDocumentRequest(type.getId(), "Needed for an external course.",
                LocalDate.now().plusDays(2), DeliveryMethod.ELECTRONIC);
        var created = service.create(a.getId(), submission, List.of(
                new MockMultipartFile("files", "../student-ID.pdf", "application/pdf", PDF),
                new MockMultipartFile("files", "photo.png", "image/png", PNG)));
        assertThat(created.collectionLocation()).isNull();
        assertThat(created.collectionInstructions()).isNull();
        assertThat(created.attachments()).hasSize(2);
        assertThat(created.attachments()).extracting(com.unidoc.common.api.AttachmentMetadata::kind)
                .containsOnly(AttachmentKind.INITIAL_SUPPORT);
        for (var metadata : created.attachments()) {
            var entity = attachments.findById(metadata.id()).orElseThrow();
            assertThat(entity.getRelativePath()).startsWith("requests/" + created.id() + "/supporting/");
            assertThat(entity.getRelativePath()).doesNotContain("..", "\\", "student-ID");
            assertThat(entity.getStoredFilename()).matches("[0-9a-f-]{36}\\.(pdf|png)");
            assertThat(Files.exists(Path.of("target/issue11-request-service-test-uploads")
                    .resolve(entity.getRelativePath()))).isTrue();
            var downloaded = service.downloadAttachment(a.getId(), created.id(), metadata.id());
            assertThat(downloaded.contentType()).isEqualTo(metadata.contentType());
            assertThat((long) downloaded.bytes().length).isEqualTo(metadata.sizeBytes());
            assertThat(downloaded.displayFilename()).isEqualTo(metadata.originalFilename());
            assertThatThrownBy(() -> service.downloadAttachment(b.getId(), created.id(), metadata.id()))
                    .isInstanceOf(DocumentRequestNotFoundException.class);
        }
        assertThatThrownBy(() -> service.getDetail(b.getId(), created.id()))
                .isInstanceOf(DocumentRequestNotFoundException.class);
        assertThatThrownBy(() -> service.downloadAttachment(a.getId(), created.id(), 9999999L))
                .isInstanceOf(com.unidoc.file.AttachmentNotFoundException.class);
        var second = service.create(a.getId(), submission, List.of(
                new MockMultipartFile("files", "different.pdf", "application/pdf", PDF)));
        assertThatThrownBy(() -> service.downloadAttachment(a.getId(), created.id(), second.attachments().getFirst().id()))
                .isInstanceOf(com.unidoc.file.AttachmentNotFoundException.class);
    }

    @Test
    void validatesProfileDocumentTypeMethodRequiredDateAndSupportingFileCollectionBeforeInsert() {
        User student = studentWithProfile();
        User newStudent = user(UserRole.STUDENT, UserStatus.ACTIVE);
        User admin = user(UserRole.ADMIN, UserStatus.ACTIVE);
        DocumentType required = type(true, false, true, "Desk");
        long originalCount = requests.count();
        var base = new CreateDocumentRequest(required.getId(), "Student scholarship application.",
                LocalDate.now().plusDays(4), DeliveryMethod.PHYSICAL);
        assertThatThrownBy(() -> service.create(newStudent.getId(), base, null))
                .isInstanceOf(ProfileIncompleteException.class);
        assertThatThrownBy(() -> service.create(admin.getId(), base, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(student.getId(), base, null))
                .isInstanceOf(FileValidationException.class);
        assertThatThrownBy(() -> service.create(student.getId(),
                new CreateDocumentRequest(required.getId(), base.purpose(), base.requiredDate(), DeliveryMethod.ELECTRONIC), null))
                .isInstanceOf(InvalidDeliveryMethodException.class);
        assertThatThrownBy(() -> service.create(student.getId(),
                new CreateDocumentRequest(required.getId(), base.purpose(), LocalDate.now().minusDays(1), DeliveryMethod.PHYSICAL), null))
                .isInstanceOf(InvalidRequiredDateException.class);
        assertThatThrownBy(() -> service.create(student.getId(),
                new CreateDocumentRequest(9999999L, base.purpose(), base.requiredDate(), DeliveryMethod.PHYSICAL), null))
                .isInstanceOf(com.unidoc.catalogue.DocumentTypeNotFoundException.class);
        required.changeActive(false, admin);
        types.saveAndFlush(required);
        assertThatThrownBy(() -> service.create(student.getId(), base, null))
                .isInstanceOf(DocumentTypeInactiveException.class);
        assertThatThrownBy(() -> service.create(student.getId(),
                new CreateDocumentRequest(required.getId(), " x ", base.requiredDate(), DeliveryMethod.PHYSICAL), null))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(requests.count()).isEqualTo(originalCount);
    }

    @Test
    void ownerListsArePaginatedFilteredAndOnlyContainOwnRequests() {
        User a = studentWithProfile();
        User b = studentWithProfile();
        DocumentType type = type(false, true, true, "Desk");
        var electronic = new CreateDocumentRequest(type.getId(), "First request for personal use.",
                LocalDate.now().plusDays(1), DeliveryMethod.ELECTRONIC);
        var physical = new CreateDocumentRequest(type.getId(), "Second request for official needs.",
                LocalDate.now().plusDays(3), DeliveryMethod.PHYSICAL);
        var aOne = service.create(a.getId(), electronic, null);
        var aTwo = service.create(a.getId(), physical, null);
        var bOne = service.create(b.getId(), physical, null);
        var page = service.list(a.getId(), "SUBMITTED", null, 0, 1, "requestNumber", "asc");
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo(aOne.id());
        var filtered = service.list(a.getId(), null, "PHYSICAL", 0, 20, null, null);
        assertThat(filtered.totalItems()).isEqualTo(1);
        assertThat(filtered.items().getFirst().id()).isEqualTo(aTwo.id());
        assertThat(service.list(b.getId(), null, null, 0, 20, null, null).items())
                .extracting(StudentRequestSummary::id).containsExactly(bOne.id());
        assertThat(service.list(a.getId(), "REJECTED", null, 0, 20, null, null).items()).isEmpty();
        assertThatThrownBy(() -> service.list(a.getId(), null, null, -1, 20, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.list(a.getId(), null, null, 0, 101, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.list(a.getId(), null, null, 0, 20, "student.passwordHash", "asc"))
                .isInstanceOf(InvalidQueryParameterException.class);
    }

    private User user(UserRole role, UserStatus status) {
        return users.saveAndFlush(new User("req-service-" + UUID.randomUUID() + "@example.test", "hash",
                "Request Service Account", role, status));
    }
    private User studentWithProfile() {
        User user = user(UserRole.STUDENT, UserStatus.ACTIVE);
        profiles.saveAndFlush(new StudentProfile(user, "REG-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Faculty of Science", "Department of Computing", "Computer Science", 2));
        return user;
    }
    private DocumentType type(boolean required, boolean electronic, boolean physical, String location) {
        User admin = user(UserRole.ADMIN, UserStatus.ACTIVE);
        String code = "REQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Document " + code,
                "A document available to registered university students.", "Provide necessary supporting records.",
                required, electronic, physical, physical ? location : null,
                physical ? "Bring identification." : null, admin));
    }
}
