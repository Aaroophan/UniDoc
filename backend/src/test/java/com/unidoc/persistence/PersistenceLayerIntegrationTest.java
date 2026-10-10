package com.unidoc.persistence;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.catalogue.DocumentTypeSpecifications;
import com.unidoc.notification.Notification;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.notification.NotificationType;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.request.AttachmentKind;
import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.DocumentRequest;
import com.unidoc.request.DocumentRequestRepository;
import com.unidoc.request.DocumentRequestSpecifications;
import com.unidoc.request.IssuedDocument;
import com.unidoc.request.IssuedDocumentRepository;
import com.unidoc.request.RequestAttachment;
import com.unidoc.request.RequestAttachmentRepository;
import com.unidoc.request.RequestStatus;
import com.unidoc.request.RequestStatusHistory;
import com.unidoc.request.RequestStatusHistoryRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserSpecifications;
import com.unidoc.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa",
        "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/persistence-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class PersistenceLayerIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$012345678901234567890u012345678901234567890123456789012";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private DocumentRequestRepository documentRequestRepository;

    @Autowired
    private RequestAttachmentRepository requestAttachmentRepository;

    @Autowired
    private IssuedDocumentRepository issuedDocumentRepository;

    @Autowired
    private RequestStatusHistoryRepository requestStatusHistoryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOnlyTheApprovedEightApplicationTables() {
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList("""
                select lower(table_name)
                from information_schema.tables
                where lower(table_schema) = 'public'
                  and table_type = 'BASE TABLE'
                """, String.class));

        assertThat(tables).containsExactlyInAnyOrder(
                "users",
                "student_profiles",
                "document_types",
                "document_requests",
                "request_attachments",
                "issued_documents",
                "request_status_history",
                "notifications"
        );
    }

    @Test
    void persistsAndReloadsTheCompleteEightEntityGraph() {
        User student = saveUser("graph-student", UserRole.STUDENT);
        User staff = saveUser("graph-staff", UserRole.STAFF);
        User admin = saveUser("graph-admin", UserRole.ADMIN);

        StudentProfile profile = studentProfileRepository.saveAndFlush(new StudentProfile(
                student,
                " cs/2026-001 ",
                "+94 77 000 0000",
                "Faculty of Science",
                "Department of Computing",
                "BSc in Computer Science",
                3
        ));
        DocumentType type = saveDocumentType(admin, "Transcript", "TRN");
        DocumentRequest request = saveRequest(student, type, DeliveryMethod.ELECTRONIC);
        request.updateStatus(RequestStatus.PROCESSING);
        documentRequestRepository.saveAndFlush(request);

        RequestAttachment attachment = requestAttachmentRepository.saveAndFlush(new RequestAttachment(
                request,
                student,
                AttachmentKind.INITIAL_SUPPORT,
                "identity.pdf",
                unique("attachment") + ".pdf",
                "requests/" + request.getId() + "/supporting/identity.pdf",
                "application/pdf",
                512
        ));
        IssuedDocument issuedDocument = issuedDocumentRepository.saveAndFlush(new IssuedDocument(
                request,
                staff,
                "transcript.pdf",
                unique("issued") + ".pdf",
                "requests/" + request.getId() + "/issued/transcript.pdf",
                "application/pdf",
                1_024
        ));
        RequestStatusHistory history = requestStatusHistoryRepository.saveAndFlush(new RequestStatusHistory(
                request,
                RequestStatus.APPROVED,
                RequestStatus.PROCESSING,
                staff,
                "Processing started"
        ));
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                student,
                NotificationType.PROCESSING_STARTED,
                "Request processing started",
                "Your request is being processed.",
                "/requests/" + request.getId()
        ));

        entityManager.clear();

        DocumentRequest reloaded = documentRequestRepository.findByRequestNumber(request.getRequestNumber()).orElseThrow();
        assertThat(reloaded.getRequestNumber()).matches("UDR-\\d{6,}");
        assertThat(reloaded.getDocumentTypeNameSnapshot()).isEqualTo("Transcript");
        assertThat(reloaded.getDeliveryMethod()).isEqualTo(DeliveryMethod.ELECTRONIC);
        assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getCreatedAt().atOffset(ZoneOffset.UTC).getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(reloaded.getVersion()).isPositive();

        assertThat(studentProfileRepository.findById(profile.getId()).orElseThrow().getRegistrationNumber())
                .isEqualTo("CS/2026-001");
        assertThat(requestAttachmentRepository.findById(attachment.getId()).orElseThrow().getAttachmentKind())
                .isEqualTo(AttachmentKind.INITIAL_SUPPORT);
        assertThat(issuedDocumentRepository.findById(issuedDocument.getId()).orElseThrow().getContentType())
                .isEqualTo("application/pdf");
        assertThat(requestStatusHistoryRepository.findById(history.getId()).orElseThrow().getNewStatus())
                .isEqualTo(RequestStatus.PROCESSING);
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getType())
                .isEqualTo(NotificationType.PROCESSING_STARTED);
    }

    @Test
    void normalizesEmailAndSupportsPageableRoleStatusAndUserFilters() {
        String marker = unique("lookup");
        User staff = userRepository.saveAndFlush(new User(
                "  " + marker.toUpperCase(Locale.ROOT) + "@Example.TEST  ",
                PASSWORD_HASH,
                "Lookup Staff",
                UserRole.STAFF
        ));

        assertThat(userRepository.findByNormalizedEmail(marker + "@EXAMPLE.test"))
                .contains(staff);
        assertThat(userRepository.existsByNormalizedEmail(marker.toUpperCase(Locale.ROOT) + "@example.test"))
                .isTrue();

        Page<User> activeStaff = userRepository.findByRoleAndStatus(
                UserRole.STAFF,
                UserStatus.ACTIVE,
                PageRequest.of(0, 1)
        );
        assertThat(activeStaff.getSize()).isEqualTo(1);
        assertThat(userRepository.findAll(
                UserSpecifications.filtered(marker, UserRole.STAFF, UserStatus.ACTIVE),
                PageRequest.of(0, 10)
        )).extracting(User::getId).containsExactly(staff.getId());
    }

    @Test
    void enforcesUniqueNormalizedEmail() {
        String email = unique("duplicate-email") + "@example.test";
        userRepository.saveAndFlush(new User(email, PASSWORD_HASH, "First", UserRole.STUDENT));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User(
                "  " + email.toUpperCase(Locale.ROOT) + "  ",
                PASSWORD_HASH,
                "Second",
                UserRole.STUDENT
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesProfileOwnershipAndRegistrationUniquenessAndAcceptsBoundaryYears() {
        User first = saveUser("profile-first", UserRole.STUDENT);
        User second = saveUser("profile-second", UserRole.STUDENT);
        String registration = unique("reg").toUpperCase(Locale.ROOT);

        studentProfileRepository.saveAndFlush(profile(first, registration, 1));
        StudentProfile upperBoundary = studentProfileRepository.saveAndFlush(profile(second, unique("reg"), 8));

        assertThat(studentProfileRepository.findByUserId(first.getId())).isPresent();
        assertThat(studentProfileRepository.findByNormalizedRegistrationNumber(
                "  " + registration.toLowerCase(Locale.ROOT) + "  "
        )).isPresent();
        assertThat(studentProfileRepository.normalizedRegistrationNumberExistsForAnotherProfile(
                registration,
                studentProfileRepository.findByUserId(first.getId()).orElseThrow().getId()
        )).isFalse();
        assertThat(upperBoundary.getAcademicYear()).isEqualTo(8);
    }

    @Test
    void rejectsASecondProfileForTheSameUser() {
        User student = saveUser("profile-owner", UserRole.STUDENT);
        studentProfileRepository.saveAndFlush(profile(student, unique("reg-one"), 1));

        assertThatThrownBy(() -> studentProfileRepository.saveAndFlush(
                profile(student, unique("reg-two"), 2)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateNormalizedRegistrationNumber() {
        User first = saveUser("registration-first", UserRole.STUDENT);
        User second = saveUser("registration-second", UserRole.STUDENT);
        String registration = unique("shared-reg");
        studentProfileRepository.saveAndFlush(profile(first, registration, 3));

        assertThatThrownBy(() -> studentProfileRepository.saveAndFlush(
                profile(second, registration.toLowerCase(Locale.ROOT), 4)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsTheActiveCatalogueInNameOrderAndSupportsAdminFiltering() {
        User admin = saveUser("catalogue-admin", UserRole.ADMIN);
        String marker = unique("catalogue");
        DocumentType zulu = saveDocumentType(admin, marker + " Zulu", unique("z"));
        DocumentType alpha = saveDocumentType(admin, marker + " Alpha", unique("a"));

        List<DocumentType> active = documentTypeRepository.findByActiveTrueOrderByNameAsc();
        assertThat(active).filteredOn(type -> type.getName().startsWith(marker))
                .extracting(DocumentType::getId)
                .containsExactly(alpha.getId(), zulu.getId());

        Page<DocumentType> filtered = documentTypeRepository.findAll(
                DocumentTypeSpecifications.filtered(marker + " alpha", true),
                PageRequest.of(0, 10)
        );
        assertThat(filtered).extracting(DocumentType::getId).containsExactly(alpha.getId());
        assertThat(documentTypeRepository.findByNormalizedCode(" " + alpha.getCode().toLowerCase(Locale.ROOT) + " "))
                .contains(alpha);
    }

    @Test
    void rejectsDuplicateDocumentTypeCode() {
        User admin = saveUser("code-admin", UserRole.ADMIN);
        String code = unique("DUP-CODE");
        saveDocumentType(admin, unique("First type"), code);

        assertThatThrownBy(() -> saveDocumentType(admin, unique("Second type"), code.toLowerCase(Locale.ROOT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateDocumentTypeName() {
        User admin = saveUser("name-admin", UserRole.ADMIN);
        String name = unique("Duplicate name");
        saveDocumentType(admin, name, unique("FIRST"));

        assertThatThrownBy(() -> saveDocumentType(admin, name, unique("SECOND")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appliesOwnerScopedAndStaffAdminRequestFiltersInTheDatabase() {
        User student = saveUser("filter-student", UserRole.STUDENT);
        User otherStudent = saveUser("filter-other", UserRole.STUDENT);
        User admin = saveUser("filter-admin", UserRole.ADMIN);
        String registration = unique("FILTER-REG").toUpperCase(Locale.ROOT);
        studentProfileRepository.saveAndFlush(profile(student, registration, 3));
        studentProfileRepository.saveAndFlush(profile(otherStudent, unique("OTHER-REG"), 2));
        DocumentType type = saveDocumentType(admin, unique("Filter Transcript"), unique("FILTER-TYPE"));
        LocalDate requiredDate = LocalDate.now(ZoneOffset.UTC).plusDays(7);

        DocumentRequest expected = saveRequest(student, type, DeliveryMethod.ELECTRONIC, requiredDate);
        saveRequest(otherStudent, type, DeliveryMethod.PHYSICAL, requiredDate.plusDays(1));

        Page<DocumentRequest> studentPage = documentRequestRepository.findAll(
                DocumentRequestSpecifications.forStudent(student.getId(), RequestStatus.SUBMITTED, null),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        assertThat(studentPage).extracting(DocumentRequest::getId).containsExactly(expected.getId());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Page<DocumentRequest> staffPage = documentRequestRepository.findAll(
                DocumentRequestSpecifications.forStaffOrAdmin(
                        registration.toLowerCase(Locale.ROOT),
                        RequestStatus.SUBMITTED,
                        type.getId(),
                        DeliveryMethod.ELECTRONIC,
                        today,
                        today,
                        requiredDate
                ),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(staffPage.getSize()).isEqualTo(1);
        assertThat(staffPage).extracting(DocumentRequest::getId).containsExactly(expected.getId());
        assertThat(documentRequestRepository.findByIdAndStudentId(expected.getId(), student.getId()))
                .contains(expected);
        assertThat(documentRequestRepository.findByIdAndStudentId(expected.getId(), otherStudent.getId()))
                .isEmpty();

        assertThat(documentRequestRepository.countGroupedByStatus())
                .anySatisfy(count -> {
                    assertThat(count.status()).isEqualTo(RequestStatus.SUBMITTED);
                    assertThat(count.count()).isGreaterThanOrEqualTo(2);
                });
        assertThat(documentRequestRepository.countGroupedByDeliveryMethod())
                .extracting(count -> count.deliveryMethod())
                .contains(DeliveryMethod.ELECTRONIC, DeliveryMethod.PHYSICAL);
        assertThat(documentRequestRepository.countGroupedByDocumentType())
                .anySatisfy(count -> {
                    assertThat(count.documentTypeId()).isEqualTo(type.getId());
                    assertThat(count.count()).isGreaterThanOrEqualTo(2);
                });
        assertThat(documentRequestRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, 10)).getSize())
                .isEqualTo(10);
    }

    @Test
    void rejectsASecondIssuedDocumentForTheSameRequest() {
        User student = saveUser("issued-student", UserRole.STUDENT);
        User staff = saveUser("issued-staff", UserRole.STAFF);
        User admin = saveUser("issued-admin", UserRole.ADMIN);
        DocumentType type = saveDocumentType(admin, unique("Issued Type"), unique("ISSUED"));
        DocumentRequest request = saveRequest(student, type, DeliveryMethod.ELECTRONIC);

        issuedDocumentRepository.saveAndFlush(issued(request, staff, "first"));

        assertThatThrownBy(() -> issuedDocumentRepository.saveAndFlush(issued(request, staff, "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsHistoryDeterministicallyAndNotificationsNewestFirstWithUnreadCount() {
        User student = saveUser("order-student", UserRole.STUDENT);
        User staff = saveUser("order-staff", UserRole.STAFF);
        User admin = saveUser("order-admin", UserRole.ADMIN);
        DocumentRequest request = saveRequest(
                student,
                saveDocumentType(admin, unique("Order Type"), unique("ORDER")),
                DeliveryMethod.PHYSICAL
        );

        RequestStatusHistory first = requestStatusHistoryRepository.saveAndFlush(new RequestStatusHistory(
                request,
                null,
                RequestStatus.SUBMITTED,
                student,
                "Submitted"
        ));
        RequestStatusHistory second = requestStatusHistoryRepository.saveAndFlush(new RequestStatusHistory(
                request,
                RequestStatus.SUBMITTED,
                RequestStatus.UNDER_REVIEW,
                staff,
                "Review started"
        ));
        Notification older = notificationRepository.saveAndFlush(new Notification(
                student,
                NotificationType.REQUEST_SUBMITTED,
                "Submitted",
                "Request submitted.",
                "/requests/" + request.getId()
        ));
        Notification newer = notificationRepository.saveAndFlush(new Notification(
                student,
                NotificationType.PHYSICAL_DOCUMENT_READY,
                "Ready",
                "Document ready.",
                "/requests/" + request.getId()
        ));

        assertThat(requestStatusHistoryRepository.findByRequestIdOrderByCreatedAtAscIdAsc(
                request.getId(),
                PageRequest.of(0, 20)
        )).extracting(RequestStatusHistory::getId).containsExactly(first.getId(), second.getId());
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(
                student.getId(),
                PageRequest.of(0, 20)
        )).extracting(Notification::getId).containsExactly(newer.getId(), older.getId());
        assertThat(notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDescIdDesc(
                student.getId(),
                false,
                PageRequest.of(0, 20)
        )).hasSize(2);
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(student.getId())).isEqualTo(2);
    }

    @Test
    void databaseCascadesOnlyTheDocumentRequestChildrenAndUserOwnedProfileNotifications() {
        User student = saveUser("cascade-student", UserRole.STUDENT);
        User staff = saveUser("cascade-staff", UserRole.STAFF);
        User admin = saveUser("cascade-admin", UserRole.ADMIN);
        StudentProfile profile = studentProfileRepository.saveAndFlush(profile(student, unique("CASCADE-REG"), 2));
        DocumentRequest request = saveRequest(
                student,
                saveDocumentType(admin, unique("Cascade Type"), unique("CASCADE")),
                DeliveryMethod.ELECTRONIC
        );
        RequestAttachment attachment = requestAttachmentRepository.saveAndFlush(new RequestAttachment(
                request,
                student,
                AttachmentKind.INITIAL_SUPPORT,
                "support.pdf",
                unique("cascade-attachment") + ".pdf",
                "requests/" + request.getId() + "/supporting/cascade.pdf",
                "application/pdf",
                100
        ));
        IssuedDocument issued = issuedDocumentRepository.saveAndFlush(issued(request, staff, "cascade"));
        RequestStatusHistory history = requestStatusHistoryRepository.saveAndFlush(new RequestStatusHistory(
                request,
                null,
                RequestStatus.SUBMITTED,
                student,
                null
        ));
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                student,
                NotificationType.REQUEST_SUBMITTED,
                "Submitted",
                "Submitted",
                "/requests/" + request.getId()
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.update("delete from document_requests where id = ?", request.getId())).isEqualTo(1);
        assertThat(requestAttachmentRepository.existsById(attachment.getId())).isFalse();
        assertThat(issuedDocumentRepository.existsById(issued.getId())).isFalse();
        assertThat(requestStatusHistoryRepository.existsById(history.getId())).isFalse();

        assertThat(jdbcTemplate.update("delete from users where id = ?", student.getId())).isEqualTo(1);
        assertThat(studentProfileRepository.existsById(profile.getId())).isFalse();
        assertThat(notificationRepository.existsById(notification.getId())).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsAStaleConcurrentRequestUpdate() {
        TransactionTemplate setup = new TransactionTemplate(transactionManager);
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long requestId = setup.execute(status -> {
            User student = saveUser("lock-student", UserRole.STUDENT);
            User admin = saveUser("lock-admin", UserRole.ADMIN);
            DocumentType type = saveDocumentType(admin, unique("Lock Type"), unique("LOCK"));
            return saveRequest(student, type, DeliveryMethod.ELECTRONIC).getId();
        });
        assertThat(requestId).isNotNull();

        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager staleManager = entityManagerFactory.createEntityManager();
        try {
            firstManager.getTransaction().begin();
            staleManager.getTransaction().begin();
            DocumentRequest firstCopy = firstManager.find(DocumentRequest.class, requestId);
            DocumentRequest staleCopy = staleManager.find(DocumentRequest.class, requestId);

            firstCopy.updateStatus(RequestStatus.UNDER_REVIEW);
            firstManager.getTransaction().commit();

            staleCopy.updateStatus(RequestStatus.APPROVED);
            Throwable failure = catchThrowable(staleManager.getTransaction()::commit);

            assertThat(failure).isNotNull();
            assertThat(hasCause(failure, OptimisticLockException.class)
                    || hasCause(failure, RollbackException.class)).isTrue();
        } finally {
            if (firstManager.getTransaction().isActive()) {
                firstManager.getTransaction().rollback();
            }
            if (staleManager.getTransaction().isActive()) {
                staleManager.getTransaction().rollback();
            }
            firstManager.close();
            staleManager.close();
        }
    }

    private User saveUser(String label, UserRole role) {
        return userRepository.saveAndFlush(new User(
                unique(label) + "@example.test",
                PASSWORD_HASH,
                label,
                role
        ));
    }

    private StudentProfile profile(User student, String registrationNumber, int academicYear) {
        return new StudentProfile(
                student,
                registrationNumber,
                null,
                "Faculty of Science",
                "Department of Computing",
                "BSc in Computer Science",
                academicYear
        );
    }

    private DocumentType saveDocumentType(User admin, String name, String code) {
        return documentTypeRepository.saveAndFlush(new DocumentType(
                code,
                name,
                "Description",
                "Requirements",
                false,
                true,
                true,
                "University Registry",
                "Bring identification.",
                admin
        ));
    }

    private DocumentRequest saveRequest(User student, DocumentType type, DeliveryMethod method) {
        return saveRequest(student, type, method, LocalDate.now(ZoneOffset.UTC).plusDays(7));
    }

    private DocumentRequest saveRequest(
            User student,
            DocumentType type,
            DeliveryMethod method,
            LocalDate requiredDate
    ) {
        DocumentRequest request = documentRequestRepository.saveAndFlush(new DocumentRequest(
                student,
                type,
                method,
                "Academic application",
                requiredDate
        ));
        request.assignRequestNumber();
        return documentRequestRepository.saveAndFlush(request);
    }

    private IssuedDocument issued(DocumentRequest request, User staff, String label) {
        String uniqueLabel = unique(label);
        return new IssuedDocument(
                request,
                staff,
                label + ".pdf",
                uniqueLabel + ".pdf",
                "requests/" + request.getId() + "/issued/" + uniqueLabel + ".pdf",
                "application/pdf",
                1_024
        );
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String unique(String label) {
        return (label + "-" + UUID.randomUUID().toString().substring(0, 8)).replace(' ', '-');
    }
}
