package com.unidoc.request;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.PreparedFileDownload;
import com.unidoc.file.StorageCategory;
import com.unidoc.file.ValidatedUpload;
import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationService;
import com.unidoc.notification.NotificationType;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Public electronic-delivery use cases. Issued files are staged outside a transaction,
 * then a distinct transactional writer commits request, issued metadata, history and notice.
 * A failed commit compensates only the files staged for that exact upload operation.
 */
@Service
public class ElectronicDocumentService {
    private static final Logger log = LoggerFactory.getLogger(ElectronicDocumentService.class);

    private final UserRepository users;
    private final DocumentRequestRepository requests;
    private final IssuedDocumentRepository issued;
    private final FileStorageService storage;
    private final RequestTransitionValidator transitions;
    private final RequestStatusHistoryRepository history;
    private final NotificationService notifications;
    private final TransactionalElectronicDocumentWriter writer;
    private final StaffRequestService staffDetails;
    private final Clock clock;

    public ElectronicDocumentService(UserRepository users, DocumentRequestRepository requests,
                                     IssuedDocumentRepository issued, FileStorageService storage,
                                     RequestTransitionValidator transitions, RequestStatusHistoryRepository history,
                                     NotificationService notifications, TransactionalElectronicDocumentWriter writer,
                                     StaffRequestService staffDetails, Clock clock) {
        this.users = users;
        this.requests = requests;
        this.issued = issued;
        this.storage = storage;
        this.transitions = transitions;
        this.history = history;
        this.notifications = notifications;
        this.writer = writer;
        this.staffDetails = staffDetails;
        this.clock = clock;
    }

    public StaffRequestDetailResponse upload(long staffUserId, long requestId,
                                             Long expectedVersion, MultipartFile file) {
        requireUser(staffUserId, UserRole.STAFF);
        if (expectedVersion == null || expectedVersion < 0) {
            throw new com.unidoc.common.exception.InvalidQueryParameterException(
                    "expectedVersion is required and must be non-negative.");
        }
        DocumentRequest request = requireRequest(requestId);
        requireUploadPreconditions(request, expectedVersion);
        ValidatedUpload checked = storage.validateIssuedPdf(file);
        FileStorageService.StorageOperation operation = storage.stage(List.of(checked), StorageCategory.ISSUED);
        try {
            // Proxied writer returns only after DB commit. Failures (including commit failures) cleanup.
            writer.upload(staffUserId, requestId, expectedVersion, operation);
        } catch (RuntimeException | Error failure) {
            storage.cleanup(operation);
            throw failure;
        }
        log.info("Issued electronic PDF committed requestId={} staffUserId={}", requestId, staffUserId);
        return staffDetails.getDetail(staffUserId, requestId);
    }

    /** The transactional writer rechecks this after staging and before finalization. */
    void requireUploadPreconditions(DocumentRequest request, long expectedVersion) {
        if (request.getVersion() != expectedVersion) throw new RequestVersionConflictException();
        if (request.getDeliveryMethod() != DeliveryMethod.ELECTRONIC) {
            throw new DeliveryMethodMismatchException();
        }
        if (issued.existsByRequestId(request.getId())) throw new IssuedDocumentAlreadyExistsException();
        transitions.requireAction(RequestAction.UPLOAD_ISSUED_DOCUMENT, UserRole.STAFF,
                request.getStatus(), request.getDeliveryMethod());
        transitions.requireTransition(request.getStatus(), RequestStatus.READY, request.getDeliveryMethod());
    }

    /**
     * Transaction begins before file read and commits before HTTP response starts.
     * No status, history, count, or notification change is possible if the PDF read fails.
     * Concurrent READY downloads serialize logically via the request's JPA optimistic version;
     * later COMPLETED downloads use an atomic SQL increment to avoid lost counts.
     */
    @Transactional
    public PreparedAttachmentDownload downloadForStudent(long studentUserId, long requestId) {
        User student = requireUser(studentUserId, UserRole.STUDENT);
        if (requestId < 1) throw new DocumentRequestNotFoundException();
        DocumentRequest request = requests.findByIdAndStudentId(requestId, studentUserId)
                .orElseThrow(DocumentRequestNotFoundException::new);
        ensureStudentDocumentReady(request);
        IssuedDocument document = issued.findByRequestId(requestId)
                .orElseThrow(IssuedDocumentNotFoundException::new);
        PreparedFileDownload prepared = storage.readAuthorized(
                document.getRelativePath(), document.getContentType(), document.getSizeBytes());
        if (!"application/pdf".equals(prepared.contentType())) throw new IssuedDocumentNotFoundException();
        Instant now = Instant.now(clock);
        if (request.getStatus() == RequestStatus.READY) {
            transitions.requireAction(RequestAction.DOWNLOAD_ISSUED_DOCUMENT, UserRole.STUDENT,
                    request.getStatus(), request.getDeliveryMethod());
            transitions.requireTransition(RequestStatus.READY, RequestStatus.COMPLETED, request.getDeliveryMethod());
            request.markElectronicCompleted(now);
            request.updateStatus(RequestStatus.COMPLETED);
            requests.saveAndFlush(request); // Competing first download must fail/roll back.
            history.saveAndFlush(new RequestStatusHistory(request, RequestStatus.READY,
                    RequestStatus.COMPLETED, student, "Electronic document first downloaded."));
            notifications.create(new NotificationCommand(studentUserId, NotificationType.REQUEST_COMPLETED,
                    "Document request completed", "Your electronic document request "
                    + request.getRequestNumber() + " is completed.", "/requests/" + requestId));
        }
        if (issued.recordStudentDownload(document.getId(), now) != 1) {
            throw new IssuedDocumentNotFoundException();
        }
        log.info("Student electronic PDF prepared requestId={} studentUserId={} firstDownload={}",
                requestId, studentUserId, request.getCompletedAt() != null && request.getCompletedAt().equals(now));
        return new PreparedAttachmentDownload(prepared.bytes(), document.getOriginalFilename(), "application/pdf");
    }

    @Transactional(readOnly = true)
    public PreparedAttachmentDownload downloadForStaff(long staffUserId, long requestId) {
        requireUser(staffUserId, UserRole.STAFF);
        DocumentRequest request = requireRequest(requestId);
        if (request.getDeliveryMethod() != DeliveryMethod.ELECTRONIC) {
            throw new DeliveryMethodMismatchException();
        }
        if (request.getStatus() != RequestStatus.READY && request.getStatus() != RequestStatus.COMPLETED) {
            throw new DocumentNotReadyException();
        }
        IssuedDocument document = issued.findByRequestId(requestId)
                .orElseThrow(IssuedDocumentNotFoundException::new);
        PreparedFileDownload prepared = storage.readAuthorized(
                document.getRelativePath(), document.getContentType(), document.getSizeBytes());
        if (!"application/pdf".equals(prepared.contentType())) throw new IssuedDocumentNotFoundException();
        log.info("Staff electronic PDF verified requestId={} staffUserId={}", requestId, staffUserId);
        return new PreparedAttachmentDownload(prepared.bytes(), document.getOriginalFilename(), "application/pdf");
    }

    private void ensureStudentDocumentReady(DocumentRequest request) {
        if (request.getDeliveryMethod() != DeliveryMethod.ELECTRONIC) throw new DeliveryMethodMismatchException();
        if (request.getStatus() != RequestStatus.READY && request.getStatus() != RequestStatus.COMPLETED) {
            throw new DocumentNotReadyException();
        }
    }

    private DocumentRequest requireRequest(long id) {
        if (id < 1) throw new DocumentRequestNotFoundException();
        return requests.findById(id).orElseThrow(DocumentRequestNotFoundException::new);
    }

    private User requireUser(long id, UserRole role) {
        User account = users.findById(id).orElseThrow(SessionRevokedException::new);
        if (account.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
        if (account.getRole() != role) throw new AccessDeniedException("Wrong role for electronic document operation.");
        return account;
    }
}
