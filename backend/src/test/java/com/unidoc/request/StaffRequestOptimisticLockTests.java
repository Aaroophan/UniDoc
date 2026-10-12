package com.unidoc.request;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-staff-optimistic;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue13-optimistic-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StaffRequestOptimisticLockTests {
    @Autowired private EntityManagerFactory factory;
    @Autowired private UserRepository users;
    @Autowired private StudentProfileRepository profiles;
    @Autowired private DocumentTypeRepository types;
    @Autowired private DocumentRequestService requestsService;
    @Autowired private DocumentRequestRepository requests;

    @Test
    void twoIndependentTransactionsCannotBothCommitChangesFromTheSameVersion() {
        User student = users.saveAndFlush(new User("lock-student-" + UUID.randomUUID() + "@example.test",
                "hash", "Lock Test Student", UserRole.STUDENT));
        profiles.saveAndFlush(new StudentProfile(student, "LOCK-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        User admin = users.saveAndFlush(new User("lock-admin-" + UUID.randomUUID() + "@example.test",
                "hash", "Lock Test Admin", UserRole.ADMIN));
        String code = "LK" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Type " + code,
                "For optimistic locking.", "Bring ID.", false, true, false, null, null, admin));
        var created = requestsService.create(student.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a library application.", LocalDate.now().plusDays(3), DeliveryMethod.ELECTRONIC), null);

        EntityManager first = factory.createEntityManager();
        EntityManager second = factory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            DocumentRequest a = first.find(DocumentRequest.class, created.id());
            DocumentRequest b = second.find(DocumentRequest.class, created.id());
            assertThat(a.getVersion()).isEqualTo(b.getVersion());
            a.updateStatus(RequestStatus.UNDER_REVIEW);
            first.getTransaction().commit();
            b.updateStatus(RequestStatus.APPROVED);
            assertThatThrownBy(() -> { second.flush(); second.getTransaction().commit(); })
                    .isInstanceOfAny(OptimisticLockException.class, jakarta.persistence.RollbackException.class,
                            org.hibernate.StaleObjectStateException.class);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
        }
        var stored = requests.findById(created.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(stored.getVersion()).isEqualTo(created.version() + 1);
    }
}
