package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationService;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Fail after DB status update/file promotion: neither rows nor new file bytes may survive. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-review-rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue14-review-rollback-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReviewWorkflowRollbackTests {
    @Autowired StaffRequestService staff;
    @Autowired DocumentRequestService students;
    @Autowired DocumentRequestRepository requests;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @Autowired RequestAttachmentRepository attachments;
    @Autowired RequestStatusHistoryRepository history;
    @MockitoBean NotificationService notificationService;

    @Test
    void notificationFailureAfterResponseFilePromotionRollsBackAndCompensates() throws Exception {
        User owner = student();
        User reviewer = user(UserRole.STAFF);
        var created = submit(owner);
        var review = staff.startReview(reviewer.getId(), created.id(), created.version());
        var asked = staff.requestInformation(reviewer.getId(), created.id(),
                new RequestInformationRequest("Please upload your identity document.", review.version()));
        long attachmentsBefore = attachments.count();
        long historiesBefore = history.count();
        Path root = Path.of("target/issue14-review-rollback-test-uploads");
        long filesBefore = countFiles(root);
        when(notificationService.create(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("Simulated notification insert failure"));
        assertThatThrownBy(() -> students.submitInformationResponse(owner.getId(), created.id(),
                new InformationResponseRequest("Here is the requested document.", asked.version()),
                List.of(new MockMultipartFile("files", "identity.pdf", "application/pdf",
                        "%PDF-1.7\nrollback proof".getBytes(StandardCharsets.US_ASCII)))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Simulated notification");
        assertThat(requests.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED);
        assertThat(requests.findById(created.id()).orElseThrow().getAdditionalInformationMessage())
                .isEqualTo("Please upload your identity document.");
        assertThat(history.count()).isEqualTo(historiesBefore);
        assertThat(attachments.count()).isEqualTo(attachmentsBefore);
        assertThat(countFiles(root)).isEqualTo(filesBefore);
    }

    @Test
    void notificationFailureAfterStaffApprovalDoesNotCommitStatusOrHistory() {
        User owner = student();
        User reviewer = user(UserRole.STAFF);
        var created = submit(owner);
        var review = staff.startReview(reviewer.getId(), created.id(), created.version());
        long historiesBefore = history.count();
        when(notificationService.create(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("Simulated notification insert failure"));
        assertThatThrownBy(() -> staff.approve(reviewer.getId(), created.id(), review.version()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(requests.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(history.count()).isEqualTo(historiesBefore);
    }

    private static long countFiles(Path root) throws java.io.IOException {
        if (!Files.exists(root)) return 0;
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("rollback14-" + UUID.randomUUID() + "@example.test",
                "hash", "Review Rollback", role));
    }

    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "RB14-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        return owner;
    }

    private StudentRequestDetailResponse submit(User owner) {
        User admin = user(UserRole.ADMIN);
        String code = "RB14" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Review rollback " + code,
                "Document used for a rollback test.", "Bring student card.", false, true, false,
                null, null, admin));
        return students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a programme application", LocalDate.now().plusDays(5), DeliveryMethod.ELECTRONIC),
                List.of());
    }
}
