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

/** The file must be compensated even if the transaction fails after promotion. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-electronic-rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue15-electronic-rollback-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ElectronicDocumentRollbackTests {
    @Autowired ElectronicDocumentService electronic;
    @Autowired DocumentRequestService students;
    @Autowired StaffRequestService staff;
    @Autowired IssuedDocumentRepository issued;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @MockitoBean NotificationService notifications;

    @Test
    void failedNotificationInsertRollsBackReadyAndRemovesOnlyTheNewIssuedFile() throws Exception {
        User owner = student();
        User reviewer = user(UserRole.STAFF);
        User admin = user(UserRole.ADMIN);
        String code = "ERB" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Rollback " + code,
                "Electronic issuance rollback test.", "Bring ID", false, true, false,
                null, null, admin));
        var submitted = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "For a scholarship programme", LocalDate.now().plusDays(5), DeliveryMethod.ELECTRONIC), List.of());
        var reviewing = staff.startReview(reviewer.getId(), submitted.id(), submitted.version());
        var approved = staff.approve(reviewer.getId(), submitted.id(), reviewing.version());
        var processing = staff.startProcessing(reviewer.getId(), submitted.id(), approved.version());
        Path root = Path.of("target/issue15-electronic-rollback-test-uploads");
        long filesBefore = files(root);
        long historyBefore = history.count();
        when(notifications.create(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("Simulated failed notification persistence"));
        assertThatThrownBy(() -> electronic.upload(reviewer.getId(), processing.id(), processing.version(),
                new MockMultipartFile("file", "issue.pdf", "application/pdf",
                        "%PDF-1.7\nrollback proof".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(requests.findById(submitted.id()).orElseThrow().getStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(requests.findById(submitted.id()).orElseThrow().getReadyAt()).isNull();
        assertThat(history.count()).isEqualTo(historyBefore);
        assertThat(issued.findByRequestId(submitted.id())).isEmpty();
        assertThat(files(root)).isEqualTo(filesBefore);
    }
    private static long files(Path root) throws Exception {
        if (!Files.exists(root)) return 0;
        try (Stream<Path> stream = Files.walk(root)) { return stream.filter(Files::isRegularFile).count(); }
    }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("eroll-" + UUID.randomUUID() + "@example.test", "hash", "Rollback", role));
    }
    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "ERB-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 3));
        return owner;
    }
}
