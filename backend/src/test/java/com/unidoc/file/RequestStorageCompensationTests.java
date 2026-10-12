package com.unidoc.file;

import com.unidoc.request.*;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.config.StorageProperties;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.StoragePaths;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.profile.StudentProfileService;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mimics a failure surfaced AFTER files have been moved (including a failed DB commit). */
class RequestStorageCompensationTests {
    @TempDir Path temp;

    @Test
    void callerCompensatesFinalizedFilesEvenWhenTheTransactionalWriterThrowsAfterMovingThem() throws Exception {
        var properties = new StorageProperties(temp.resolve("uploads"), 5_242_880, 10_485_760);
        var paths = new StoragePaths(properties);
        paths.initialize();
        var storage = new FileStorageService(paths, properties);
        UserRepository users = mock(UserRepository.class);
        StudentProfileService profile = mock(StudentProfileService.class);
        DocumentTypeRepository types = mock(DocumentTypeRepository.class);
        TransactionalRequestWriter writer = mock(TransactionalRequestWriter.class);
        User actor = new User("student@example.test", "hash", "A Student", UserRole.STUDENT);
        User admin = new User("admin@example.test", "hash", "An Administrator", UserRole.ADMIN);
        DocumentType type = new DocumentType("TRANSCRIPT", "Transcript", "Description for a transcript.",
                "Identification is required.", true, true, true, "Desk", null, admin);
        when(users.findById(1L)).thenReturn(Optional.of(actor));
        when(types.findById(99L)).thenReturn(Optional.of(type));
        when(writer.create(eq(1L), any(CreateDocumentRequest.class),
                any(FileStorageService.StorageOperation.class))).thenAnswer(call -> {
                    FileStorageService.StorageOperation operation = call.getArgument(2);
                    var result = storage.finalizeFiles(operation, 76L);
                    assertThat(result).hasSize(1);
                    assertThat(Files.exists(paths.storageRoot().resolve(result.getFirst().relativePath()))).isTrue();
                    throw new IllegalStateException("Simulated failed database commit");
                });

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new DocumentRequestService(users, profile, mock(com.unidoc.profile.StudentProfileRepository.class),
                    types, mock(DocumentRequestRepository.class), mock(RequestAttachmentRepository.class),
                    mock(RequestStatusHistoryRepository.class), mock(IssuedDocumentRepository.class),
                    new RequestTransitionValidator(), storage, writer, factory.getValidator(), Clock.systemUTC());
            var command = new CreateDocumentRequest(99L, "Required for a scholarship application.",
                    LocalDate.now().plusDays(3), DeliveryMethod.ELECTRONIC);
            var file = new MockMultipartFile("files", "original.pdf", "application/pdf",
                    "%PDF-1.7\ntest".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertThatThrownBy(() -> service.create(1L, command, List.of(file)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Simulated failed database commit");
        }
        try (var all = Files.walk(paths.storageRoot())) {
            assertThat(all.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }
}
