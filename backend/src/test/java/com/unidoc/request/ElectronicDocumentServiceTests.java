package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.file.FileStorageException;
import com.unidoc.file.FileValidationException;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.notification.NotificationType;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-electronic-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue15-electronic-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ElectronicDocumentServiceTests {
    static final byte[] PDF = "%PDF-1.7\nIssue 15 electronic document test".getBytes(StandardCharsets.US_ASCII);
    @Autowired ElectronicDocumentService electronic;
    @Autowired DocumentRequestService students;
    @Autowired StaffRequestService staff;
    @Autowired IssuedDocumentRepository issued;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;

    @Test
    void firstDownloadCompletesAndLaterDownloadsOnlyIncrementCounterWhileStaffVerificationIsNeutral() {
        User reviewer = user(UserRole.STAFF);
        User secondStaff = user(UserRole.STAFF);
        User owner = student();
        User stranger = student();
        User admin = user(UserRole.ADMIN);
        var processing = processing(owner, reviewer, DeliveryMethod.ELECTRONIC);
        var ready = electronic.upload(reviewer.getId(), processing.id(), processing.version(), pdf());
        assertThat(ready.status()).isEqualTo(RequestStatus.READY);
        assertThat(ready.readyAt()).isNotNull();
        assertThat(ready.completedAt()).isNull();
        assertThat(ready.version()).isEqualTo(processing.version() + 1);
        assertThat(ready.availableActions()).containsExactly(RequestAction.DOWNLOAD_ISSUED_DOCUMENT);
        assertThat(ready.issuedDocument()).isNotNull();
        assertThat(ready.issuedDocument().downloadCount()).isZero();
        assertThat(ready.issuedDocument().firstDownloadedAt()).isNull();
        assertThat(ready.history().getLast().previousStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(ready.history().getLast().newStatus()).isEqualTo(RequestStatus.READY);
        assertThat(ready.history().getLast().changedBy().id()).isEqualTo(reviewer.getId());
        assertThat(noticeCount(owner, NotificationType.ELECTRONIC_DOCUMENT_READY)).isEqualTo(1);
        assertThat(noticeCount(admin, NotificationType.ELECTRONIC_DOCUMENT_READY)).isZero();
        long historyCount = history.count();
        assertThat(electronic.downloadForStaff(secondStaff.getId(), ready.id()).bytes()).isEqualTo(PDF);
        assertThat(electronic.downloadForStaff(reviewer.getId(), ready.id()).bytes()).isEqualTo(PDF);
        assertThat(issued.findByRequestId(ready.id()).orElseThrow().getDownloadCount()).isZero();
        assertThat(requests.findById(ready.id()).orElseThrow().getStatus()).isEqualTo(RequestStatus.READY);
        assertThat(history.count()).isEqualTo(historyCount);

        assertThatThrownBy(() -> electronic.downloadForStudent(stranger.getId(), ready.id()))
                .isInstanceOf(DocumentRequestNotFoundException.class);
        assertThatThrownBy(() -> electronic.downloadForStudent(admin.getId(), ready.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> electronic.downloadForStaff(admin.getId(), ready.id()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(electronic.downloadForStudent(owner.getId(), ready.id()).bytes()).isEqualTo(PDF);
        var completed = students.getDetail(owner.getId(), ready.id());
        assertThat(completed.status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.version()).isEqualTo(ready.version() + 1);
        assertThat(completed.issuedDocument().downloadCount()).isEqualTo(1);
        assertThat(completed.issuedDocument().firstDownloadedAt()).isNotNull();
        assertThat(completed.history().getLast().previousStatus()).isEqualTo(RequestStatus.READY);
        assertThat(completed.history().getLast().newStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(completed.history().getLast().changedBy().id()).isEqualTo(owner.getId());
        assertThat(noticeCount(owner, NotificationType.REQUEST_COMPLETED)).isEqualTo(1);
        assertThat(history.count()).isEqualTo(historyCount + 1);

        assertThat(electronic.downloadForStudent(owner.getId(), ready.id()).bytes()).isEqualTo(PDF);
        assertThat(electronic.downloadForStudent(owner.getId(), ready.id()).bytes()).isEqualTo(PDF);
        var afterRepeat = students.getDetail(owner.getId(), ready.id());
        assertThat(afterRepeat.issuedDocument().downloadCount()).isEqualTo(3);
        assertThat(afterRepeat.issuedDocument().firstDownloadedAt())
                .isEqualTo(completed.issuedDocument().firstDownloadedAt());
        assertThat(issued.findByRequestId(ready.id()).orElseThrow().getLastDownloadedAt()).isNotNull();
        assertThat(afterRepeat.version()).isEqualTo(completed.version());
        assertThat(history.count()).isEqualTo(historyCount + 1);
        assertThat(noticeCount(owner, NotificationType.REQUEST_COMPLETED)).isEqualTo(1);
        assertThat(electronic.downloadForStaff(reviewer.getId(), ready.id()).bytes()).isEqualTo(PDF);
        assertThat(issued.findByRequestId(ready.id()).orElseThrow().getDownloadCount()).isEqualTo(3);
    }

    @Test
    void wrongMethodStateVersionAndInvalidFilesDoNotIssueAnything() {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        var physical = processing(owner, reviewer, DeliveryMethod.PHYSICAL);
        assertThatThrownBy(() -> electronic.upload(reviewer.getId(), physical.id(), physical.version(), pdf()))
                .isInstanceOf(DeliveryMethodMismatchException.class);
        var submitted = submit(owner, DeliveryMethod.ELECTRONIC);
        assertThatThrownBy(() -> electronic.upload(reviewer.getId(), submitted.id(), submitted.version(), pdf()))
                .isInstanceOf(InvalidRequestTransitionException.class);
        var processing = processing(owner, reviewer, DeliveryMethod.ELECTRONIC);
        assertThatThrownBy(() -> electronic.upload(reviewer.getId(), processing.id(), processing.version() - 1, pdf()))
                .isInstanceOf(RequestVersionConflictException.class);
        for (MockMultipartFile invalid : List.of(
                new MockMultipartFile("file", "wrong.png", "image/png", PDF),
                new MockMultipartFile("file", "forged.pdf", "application/pdf", "plain text".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "issued.pdf", "text/plain", PDF),
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])
        )) {
            assertThatThrownBy(() -> electronic.upload(reviewer.getId(), processing.id(), processing.version(), invalid))
                    .isInstanceOf(FileValidationException.class);
        }
        assertThat(issued.existsByRequestId(processing.id())).isFalse();
        assertThat(staff.getDetail(reviewer.getId(), processing.id()).status()).isEqualTo(RequestStatus.PROCESSING);
        var ready = electronic.upload(reviewer.getId(), processing.id(), processing.version(), pdf());
        assertThatThrownBy(() -> electronic.upload(reviewer.getId(), ready.id(), ready.version(), pdf()))
                .isInstanceOf(IssuedDocumentAlreadyExistsException.class);
        assertThat(issued.findByRequestId(ready.id())).isPresent();
        assertThatThrownBy(() -> electronic.upload(owner.getId(), processing.id(), ready.version(), pdf()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentLaterDownloadsIncrementWithoutLostCountsOrDuplicateCompletion() throws Exception {
        User owner = student();
        User reviewer = user(UserRole.STAFF);
        var processing = processing(owner, reviewer, DeliveryMethod.ELECTRONIC);
        var ready = electronic.upload(reviewer.getId(), processing.id(), processing.version(), pdf());
        electronic.downloadForStudent(owner.getId(), ready.id()); // One first-completion event.
        long countBefore = history.count();
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<byte[]>> downloads = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                downloads.add(pool.submit(() -> electronic.downloadForStudent(owner.getId(), ready.id()).bytes()));
            }
            for (Future<byte[]> result : downloads) assertThat(result.get()).isEqualTo(PDF);
        }
        assertThat(issued.findByRequestId(ready.id()).orElseThrow().getDownloadCount()).isEqualTo(5);
        assertThat(history.count()).isEqualTo(countBefore);
        assertThat(noticeCount(owner, NotificationType.REQUEST_COMPLETED)).isEqualTo(1);
    }

    @Test
    void missingOrCorruptedPdfNeverCompletesOrRecordsDownload() throws Exception {
        User owner = student();
        User reviewer = user(UserRole.STAFF);
        var processing = processing(owner, reviewer, DeliveryMethod.ELECTRONIC);
        var ready = electronic.upload(reviewer.getId(), processing.id(), processing.version(), pdf());
        IssuedDocument document = issued.findByRequestId(ready.id()).orElseThrow();
        Path file = Path.of("target/issue15-electronic-service-test-uploads").resolve(document.getRelativePath());
        assertThat(Files.isRegularFile(file)).isTrue();
        long historyBefore = history.count();
        Files.writeString(file, "PDF was corrupted after issue", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> electronic.downloadForStudent(owner.getId(), ready.id()))
                .isInstanceOfAny(FileStorageException.class, IssuedDocumentNotFoundException.class);
        assertThat(students.getDetail(owner.getId(), ready.id()).status()).isEqualTo(RequestStatus.READY);
        assertThat(issued.findByRequestId(ready.id()).orElseThrow().getDownloadCount()).isZero();
        assertThat(history.count()).isEqualTo(historyBefore);
        assertThat(noticeCount(owner, NotificationType.REQUEST_COMPLETED)).isZero();
        Files.delete(file);
        assertThatThrownBy(() -> electronic.downloadForStudent(owner.getId(), ready.id()))
                .isInstanceOfAny(FileStorageException.class, IssuedDocumentNotFoundException.class);
        assertThat(issued.findByRequestId(ready.id()).orElseThrow().getFirstDownloadedAt()).isNull();
    }

    @Test
    void issuedMetadataAndFileLocationNeverUseTheClientPath() throws Exception {
        User owner = student();
        User staffUser = user(UserRole.STAFF);
        var processing = processing(owner, staffUser, DeliveryMethod.ELECTRONIC);
        var ready = electronic.upload(staffUser.getId(), processing.id(), processing.version(),
                new MockMultipartFile("file", "C:\\temp\\résumé \"v2\".pdf", "application/pdf", PDF));
        IssuedDocument record = issued.findByRequestId(ready.id()).orElseThrow();
        assertThat(record.getRelativePath()).startsWith("requests/" + ready.id() + "/issued/");
        assertThat(record.getStoredFilename()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(record.getOriginalFilename()).doesNotContain("temp", "\\", "/");
        assertThat(record.getContentType()).isEqualTo("application/pdf");
        assertThat(record.getSizeBytes()).isEqualTo(PDF.length);
        assertThat(Files.readAllBytes(Path.of("target/issue15-electronic-service-test-uploads")
                .resolve(record.getRelativePath()))).isEqualTo(PDF);
    }

    private long noticeCount(User user, NotificationType type) {
        return notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, 100))
                .stream().filter(notification -> notification.getType() == type).count();
    }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("elc15-" + UUID.randomUUID() + "@example.test", "hash",
                "Electronic Delivery Tester", role));
    }
    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "ELC-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 3));
        return owner;
    }
    private StudentRequestDetailResponse submit(User owner, DeliveryMethod method) {
        User admin = user(UserRole.ADMIN);
        String code = "ELC" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Electronic " + code,
                "Electronic issue test document.", "Provide student ID", false, true, true,
                "Campus office", "Bring ID", admin));
        return students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a graduate application", LocalDate.now().plusDays(5), method), List.of());
    }
    private StaffRequestDetailResponse processing(User owner, User reviewer, DeliveryMethod method) {
        var created = submit(owner, method);
        var reviewed = staff.startReview(reviewer.getId(), created.id(), created.version());
        var approved = staff.approve(reviewer.getId(), created.id(), reviewed.version());
        return staff.startProcessing(reviewer.getId(), created.id(), approved.version());
    }
    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "issued.pdf", "application/pdf", PDF);
    }
}
