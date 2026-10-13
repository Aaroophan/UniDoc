package com.unidoc.demo;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.file.DemoSampleFiles;
import com.unidoc.file.StorageCategory;
import com.unidoc.file.StoredFile;
import com.unidoc.notification.Notification;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.notification.NotificationType;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.request.AttachmentKind;
import com.unidoc.request.DocumentRequest;
import com.unidoc.request.DocumentRequestRepository;
import com.unidoc.request.IssuedDocument;
import com.unidoc.request.IssuedDocumentRepository;
import com.unidoc.request.RequestAttachment;
import com.unidoc.request.RequestAttachmentRepository;
import com.unidoc.request.RequestStatus;
import com.unidoc.request.RequestStatusHistory;
import com.unidoc.request.RequestStatusHistoryRepository;
import com.unidoc.request.RequestTransitionValidator;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** Idempotent local demo seed. No web endpoint and no email events are published. */
@Service
public class DemoSeedService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;
    private final DocumentTypeRepository types;
    private final DocumentRequestRepository requests;
    private final RequestStatusHistoryRepository histories;
    private final NotificationRepository notifications;
    private final RequestAttachmentRepository attachments;
    private final IssuedDocumentRepository issued;
    private final DemoSampleFiles files;
    private final PasswordEncoder passwordEncoder;
    private final RequestTransitionValidator transitions;
    private final Clock clock;

    public DemoSeedService(UserRepository users, StudentProfileRepository profiles,
                           DocumentTypeRepository types, DocumentRequestRepository requests,
                           RequestStatusHistoryRepository histories, NotificationRepository notifications,
                           RequestAttachmentRepository attachments, IssuedDocumentRepository issued,
                           DemoSampleFiles files, PasswordEncoder passwordEncoder,
                           RequestTransitionValidator transitions, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.types = types;
        this.requests = requests;
        this.histories = histories;
        this.notifications = notifications;
        this.attachments = attachments;
        this.issued = issued;
        this.files = files;
        this.passwordEncoder = passwordEncoder;
        this.transitions = transitions;
        this.clock = clock;
    }

    public record SeedSummary(int accounts, int profiles, int types, int requests,
                              int histories, int notifications, int files) { }

    private static final class Counts {
        int accounts, profiles, types, requests, histories, notifications, files;
        SeedSummary snapshot() {
            return new SeedSummary(accounts, profiles, types, requests, histories, notifications, files);
        }
    }

    /** Existing account credentials/status, profile edits, catalogue edits and workflows remain untouched. */
    @Transactional
    public SeedSummary seed() {
        Counts count = new Counts();
        User admin = account(DemoSeedFixtures.ADMIN_EMAIL, "Demo Administrator", UserRole.ADMIN, count);
        User staff = account(DemoSeedFixtures.STAFF_EMAIL, "Demo Staff Member", UserRole.STAFF, count);
        User student = account(DemoSeedFixtures.STUDENT_EMAIL, "Demo Student One", UserRole.STUDENT, count);
        User second = account(DemoSeedFixtures.SECOND_STUDENT_EMAIL, "Demo Student Two", UserRole.STUDENT, count);
        profile(student, "DEMO-2026-001", 2, count);
        profile(second, "DEMO-2026-002", 3, count);

        Map<String, DocumentType> catalogue = new HashMap<>();
        for (DemoSeedFixtures.Type entry : DemoSeedFixtures.TYPES) {
            DocumentType type = types.findByCode(entry.code()).orElseGet(() -> {
                if (types.existsByNameIgnoreCase(entry.name())) {
                    throw new IllegalStateException("A demo catalogue name is already assigned to a different code.");
                }
                DocumentType fresh = new DocumentType(entry.code(), entry.name(), entry.description(),
                        entry.requirements(), entry.supportRequired(), true, true,
                        entry.location(), entry.instructions(), admin);
                count.types++;
                return types.saveAndFlush(fresh);
            });
            catalogue.put(entry.code(), type);
        }

        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        for (DemoSeedFixtures.RequestSample sample : DemoSeedFixtures.REQUESTS) {
            if (requests.findByRequestNumber(sample.number()).isPresent()) continue;
            User owner = sample.secondStudent() ? second : student;
            DocumentRequest request = new DocumentRequest(owner, catalogue.get(sample.typeCode()),
                    sample.method(), sample.purpose(), today.plusDays(14));
            requests.saveAndFlush(request);
            request.assignDemoRequestNumber(sample.number());
            requests.saveAndFlush(request);
            count.requests++;
            Instant now = clock.instant();
            history(request, null, RequestStatus.SUBMITTED, owner, "Request submitted.", count);
            notice(owner, NotificationType.REQUEST_SUBMITTED, "Request submitted", request, count);
            notice(staff, NotificationType.REQUEST_SUBMITTED, "New document request", request, count);
            if (request.getDocumentType().isSupportingDocumentsRequired()
                    || sample.number().endsWith("0001")) {
                supporting(request, owner, AttachmentKind.INITIAL_SUPPORT, StorageCategory.SUPPORTING, count);
            }
            if (sample.status() == RequestStatus.SUBMITTED) continue;
            transition(request, RequestStatus.UNDER_REVIEW, staff, "Review started.", now, count);

            if (sample.status() == RequestStatus.ADDITIONAL_INFORMATION_REQUIRED) {
                request.requestAdditionalInformation("Please attach confirmation of the intended recipient.");
                transition(request, RequestStatus.ADDITIONAL_INFORMATION_REQUIRED, staff,
                        request.getAdditionalInformationMessage(), now, count);
                notice(owner, NotificationType.ADDITIONAL_INFORMATION_REQUIRED,
                        "Additional information required", request, count);
                continue;
            }
            if (sample.status() == RequestStatus.UNDER_REVIEW) continue;
            if (sample.status() == RequestStatus.REJECTED) {
                request.reject("The example request did not include sufficient supporting information.");
                transition(request, RequestStatus.REJECTED, staff, request.getRejectionReason(), now, count);
                notice(owner, NotificationType.REQUEST_REJECTED, "Request rejected", request, count);
                continue;
            }
            transition(request, RequestStatus.APPROVED, staff, "Request approved.", now, count);
            notice(owner, NotificationType.REQUEST_APPROVED, "Request approved", request, count);
            if (sample.status() == RequestStatus.APPROVED) continue;
            transition(request, RequestStatus.PROCESSING, staff, "Processing started.", now, count);
            notice(owner, NotificationType.PROCESSING_STARTED, "Processing started", request, count);
            if (sample.status() == RequestStatus.PROCESSING) continue;

            if (sample.method() == com.unidoc.request.DeliveryMethod.ELECTRONIC) {
                request.markElectronicReady(now);
            } else {
                request.markPhysicalReady(now);
            }
            transition(request, RequestStatus.READY, staff,
                    sample.method() == com.unidoc.request.DeliveryMethod.ELECTRONIC
                            ? "Electronic document ready." : "Physical document ready for collection.",
                    now, count);
            if (sample.method() == com.unidoc.request.DeliveryMethod.ELECTRONIC) {
                StoredFile stored = files.ensurePdf(request.getId(), request.getRequestNumber(), StorageCategory.ISSUED);
                issued.saveAndFlush(new IssuedDocument(request, staff, stored.originalFilename(),
                        stored.storedFilename(), stored.relativePath(), stored.contentType(), stored.sizeBytes()));
                count.files++;
                notice(owner, NotificationType.ELECTRONIC_DOCUMENT_READY,
                        "Electronic document ready", request, count);
            } else {
                notice(owner, NotificationType.PHYSICAL_DOCUMENT_READY, "Physical document ready", request, count);
            }
            if (sample.status() == RequestStatus.READY) continue;
            if (sample.method() == com.unidoc.request.DeliveryMethod.ELECTRONIC) {
                request.markElectronicCompleted(now);
                issued.findByRequestId(request.getId()).ifPresent(document ->
                        issued.recordStudentDownload(document.getId(), now));
            } else {
                request.recordPhysicalCollection(today, "Demo collection: student ID checked.", now);
            }
            transition(request, RequestStatus.COMPLETED,
                    sample.method() == com.unidoc.request.DeliveryMethod.ELECTRONIC ? owner : staff,
                    sample.method() == com.unidoc.request.DeliveryMethod.PHYSICAL
                            ? request.getCollectionNote() : "Request completed.", now, count);
            notice(owner, NotificationType.REQUEST_COMPLETED, "Request completed", request, count);
        }
        return count.snapshot();
    }

    private User account(String email, String name, UserRole role, Counts count) {
        return users.findByNormalizedEmail(email).map(existing -> {
            if (existing.getRole() != role) {
                throw new IllegalStateException("An existing demo email belongs to an unexpected role.");
            }
            return existing;
        }).orElseGet(() -> {
            count.accounts++;
            return users.saveAndFlush(new User(email,
                    passwordEncoder.encode(DemoSeedFixtures.LOCAL_DEMO_PASSWORD), name, role));
        });
    }

    private void profile(User user, String registration, int year, Counts count) {
        if (profiles.findByUserId(user.getId()).isPresent()) return;
        if (profiles.existsByRegistrationNumber(registration)) {
            throw new IllegalStateException("A demo registration number is already used by another profile.");
        }
        profiles.saveAndFlush(new StudentProfile(user, registration, "+000-000-0000",
                "Faculty of Computing", "Department of Software Engineering",
                "Bachelor of Software Engineering", year));
        count.profiles++;
    }

    private void transition(DocumentRequest request, RequestStatus next, User actor,
                            String note, Instant when, Counts count) {
        RequestStatus previous = request.getStatus();
        transitions.requireTransition(previous, next, request.getDeliveryMethod());
        request.updateStatus(next);
        requests.saveAndFlush(request); // monotonic @Version consistent with regular actions
        history(request, previous, next, actor, note, count);
    }

    private void history(DocumentRequest request, RequestStatus previous, RequestStatus next,
                         User actor, String note, Counts count) {
        histories.save(new RequestStatusHistory(request, previous, next, actor, note));
        count.histories++;
    }

    private void notice(User recipient, NotificationType kind, String title,
                        DocumentRequest request, Counts count) {
        String url = recipient.getRole() == UserRole.STAFF
                ? "/staff/requests/" + request.getId() : "/requests/" + request.getId();
        notifications.save(new Notification(recipient, kind, title,
                "UniDoc demonstration request " + request.getRequestNumber() + ": " + title + ".", url));
        count.notifications++;
    }

    private void supporting(DocumentRequest request, User owner, AttachmentKind kind,
                            StorageCategory category, Counts count) {
        StoredFile stored = files.ensurePdf(request.getId(), request.getRequestNumber(), category);
        attachments.save(new RequestAttachment(request, owner, kind,
                stored.originalFilename(), stored.storedFilename(), stored.relativePath(),
                stored.contentType(), stored.sizeBytes()));
        count.files++;
    }
}
