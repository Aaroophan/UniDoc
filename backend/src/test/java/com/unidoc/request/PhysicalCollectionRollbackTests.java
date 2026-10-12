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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The same transaction owns status, dates, history, and in-app notification. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-physical-rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue16-physical-rollback-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PhysicalCollectionRollbackTests {
    @Autowired StaffRequestService staff;
    @Autowired DocumentRequestService students;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @MockitoBean NotificationService notifications;

    @Test
    void failedReadyNotificationRollsBackStatusReadyTimeAndHistory() {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        var processing = processing(reviewer, owner);
        long before = history.count();
        when(notifications.create(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("Simulated physical-ready notification failure"));
        assertThatThrownBy(() -> staff.readyForCollection(reviewer.getId(), processing.id(), processing.version()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("physical-ready");
        var stored = requests.findById(processing.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(stored.getReadyAt()).isNull();
        assertThat(stored.getVersion()).isEqualTo(processing.version());
        assertThat(history.count()).isEqualTo(before);
    }

    @Test
    void failedCompletionNotificationRollsBackCollectionDateNoteTimeStatusAndHistory() {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        var processing = processing(reviewer, owner);
        var ready = staff.readyForCollection(reviewer.getId(), processing.id(), processing.version());
        long before = history.count();
        when(notifications.create(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("Simulated collection notification failure"));
        assertThatThrownBy(() -> staff.completeCollection(reviewer.getId(), ready.id(),
                new CompleteCollectionRequest(LocalDate.now(), "  Proof checked. ", ready.version())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("collection notification");
        var stored = requests.findById(ready.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RequestStatus.READY);
        // DATETIME(6) rounds a nanosecond Instant to microsecond precision.
        assertThat(Duration.between(ready.readyAt(), stored.getReadyAt()).abs())
                .isLessThanOrEqualTo(Duration.ofNanos(1_000));
        assertThat(stored.getCompletedAt()).isNull();
        assertThat(stored.getCollectionDate()).isNull();
        assertThat(stored.getCollectionNote()).isNull();
        assertThat(stored.getVersion()).isEqualTo(ready.version());
        assertThat(history.count()).isEqualTo(before);
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("phy-roll-" + UUID.randomUUID() + "@example.test", "hash", "Collection Tester", role));
    }
    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "PHYR-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        return owner;
    }
    private StaffRequestDetailResponse processing(User reviewer, User owner) {
        User admin = user(UserRole.ADMIN);
        String code = "PHYR" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Physical Rollback " + code,
                "Physical collection rollback test.", "Bring university identification", false, false, true,
                "Student Affairs Desk", "Bring original ID.", admin));
        var submitted = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Needed for the programme application", LocalDate.now().plusDays(5), DeliveryMethod.PHYSICAL), List.of());
        var reviewing = staff.startReview(reviewer.getId(), submitted.id(), submitted.version());
        var approved = staff.approve(reviewer.getId(), submitted.id(), reviewing.version());
        return staff.startProcessing(reviewer.getId(), submitted.id(), approved.version());
    }
}
