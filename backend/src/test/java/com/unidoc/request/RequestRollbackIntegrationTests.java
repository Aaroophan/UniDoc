package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationRepository;
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

/** Real H2 transaction rolls back file metadata/history/request after a post-move failure. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-request-rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue11-request-rollback-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RequestRollbackIntegrationTests {
    @Autowired DocumentRequestService service;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestAttachmentRepository attachments;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;

    @MockitoBean NotificationService notificationService;

    @Test
    void failureAfterFilePromotionLeavesNoCommittedRowsOrNewFinalBytes() throws Exception {
        User student = users.saveAndFlush(new User("rollback-" + UUID.randomUUID() + "@example.test", "hash",
                "Transaction Tester", UserRole.STUDENT));
        User admin = users.saveAndFlush(new User("rollback-admin-" + UUID.randomUUID() + "@example.test", "hash",
                "Transaction Administrator", UserRole.ADMIN));
        profiles.saveAndFlush(new StudentProfile(student, "ROLL-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Faculty", "Department", "Degree Programme", 2));
        DocumentType type = types.saveAndFlush(new DocumentType("RB" + UUID.randomUUID().toString().substring(0, 8),
                "Rollback Document", "A document for testing database transaction rollback.",
                "Provide identification records.", true, true, false, null, null, admin));
        long beforeRequests = requests.count();
        long beforeAttachments = attachments.count();
        long beforeHistory = history.count();
        long beforeNotifications = notifications.count();
        Path root = Path.of("target/issue11-request-rollback-test-uploads");
        long beforeFiles = countRegularFiles(root);

        when(notificationService.create(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("Simulated notification write failure"));
        var body = new CreateDocumentRequest(type.getId(), "Needed for a scholarship application.",
                LocalDate.now().plusDays(5), DeliveryMethod.ELECTRONIC);
        var file = new MockMultipartFile("files", "support.pdf", "application/pdf",
                "%PDF-1.7\nrollback".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> service.create(student.getId(), body, List.of(file)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Simulated notification write failure");
        assertThat(requests.count()).isEqualTo(beforeRequests);
        assertThat(attachments.count()).isEqualTo(beforeAttachments);
        assertThat(history.count()).isEqualTo(beforeHistory);
        assertThat(notifications.count()).isEqualTo(beforeNotifications);
        assertThat(countRegularFiles(root)).isEqualTo(beforeFiles);
    }

    private static long countRegularFiles(Path path) throws java.io.IOException {
        if (!Files.exists(path)) return 0;
        try (Stream<Path> all = Files.walk(path)) {
            return all.filter(Files::isRegularFile).count();
        }
    }
}
