package com.unidoc.common.api;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.request.AttachmentKind;
import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.DocumentRequest;
import com.unidoc.request.RequestAttachment;
import com.unidoc.request.RequestStatus;
import com.unidoc.request.RequestStatusHistory;
import com.unidoc.user.User;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CommonDtoMapperTest {

    private static final String HASH = "$2a$12$012345678901234567890u012345678901234567890123456789012";

    @Test
    void userSummaryExposesOnlyPublicAccountFields() {
        User user = new User("student@example.test", HASH, "Student One", UserRole.STUDENT);

        UserSummary summary = CommonDtoMapper.toUserSummary(user);

        assertThat(summary.email()).isEqualTo("student@example.test");
        assertThat(summary.fullName()).isEqualTo("Student One");
        assertThat(summary.role()).isEqualTo(UserRole.STUDENT);
        assertThat(UserSummary.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("passwordHash", "authVersion");
    }

    @Test
    void attachmentAndHistoryMappingsDoNotExposeStoragePathsOrHashes() {
        User student = new User("student@example.test", HASH, "Student One", UserRole.STUDENT);
        User staff = new User("staff@example.test", HASH, "Staff One", UserRole.STAFF);
        User admin = new User("admin@example.test", HASH, "Admin One", UserRole.ADMIN);
        DocumentType type = new DocumentType(
                "TRANSCRIPT",
                "Academic Transcript",
                "Description",
                "Requirements",
                false,
                true,
                true,
                "Administration Office",
                "Bring identification.",
                admin
        );
        DocumentRequest request = new DocumentRequest(
                student,
                type,
                DeliveryMethod.ELECTRONIC,
                "Scholarship",
                LocalDate.of(2026, 10, 17)
        );
        RequestAttachment attachment = new RequestAttachment(
                request,
                student,
                AttachmentKind.INITIAL_SUPPORT,
                "identity.pdf",
                "secret-storage-name.pdf",
                "requests/1/supporting/secret-storage-name.pdf",
                "application/pdf",
                120
        );
        RequestStatusHistory history = new RequestStatusHistory(
                request,
                RequestStatus.SUBMITTED,
                RequestStatus.UNDER_REVIEW,
                staff,
                "Review started."
        );

        AttachmentMetadata metadata = CommonDtoMapper.toAttachmentMetadata(attachment);
        HistoryEntry historyEntry = CommonDtoMapper.toHistoryEntry(history);

        assertThat(metadata.originalFilename()).isEqualTo("identity.pdf");
        assertThat(AttachmentMetadata.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("storedFilename", "relativePath");
        assertThat(historyEntry.changedBy().fullName()).isEqualTo("Staff One");
        assertThat(ActorSummary.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("email", "passwordHash", "authVersion");
    }
}
