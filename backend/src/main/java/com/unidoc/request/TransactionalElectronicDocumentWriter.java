package com.unidoc.request;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.StoredFile;
import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationService;
import com.unidoc.notification.NotificationType;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Separate proxied transaction prevents a failed commit from escaping file compensation. */
@Service
public class TransactionalElectronicDocumentWriter {
    private final UserRepository users;
    private final DocumentRequestRepository requests;
    private final IssuedDocumentRepository issued;
    private final FileStorageService storage;
    private final RequestTransitionValidator transitions;
    private final RequestStatusHistoryRepository history;
    private final NotificationService notifications;
    private final Clock clock;

    public TransactionalElectronicDocumentWriter(UserRepository users, DocumentRequestRepository requests,
                                                 IssuedDocumentRepository issued, FileStorageService storage,
                                                 RequestTransitionValidator transitions,
                                                 RequestStatusHistoryRepository history,
                                                 NotificationService notifications, Clock clock) {
        this.users = users;
        this.requests = requests;
        this.issued = issued;
        this.storage = storage;
        this.transitions = transitions;
        this.history = history;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void upload(long staffUserId, long requestId, long expectedVersion,
                       FileStorageService.StorageOperation operation) {
        User staff = users.findById(staffUserId).orElseThrow(SessionRevokedException::new);
        if (staff.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
        if (staff.getRole() != UserRole.STAFF) throw new AccessDeniedException("Only staff may issue documents.");
        if (requestId < 1) throw new DocumentRequestNotFoundException();
        DocumentRequest request = requests.findById(requestId).orElseThrow(DocumentRequestNotFoundException::new);
        if (request.getVersion() != expectedVersion) throw new RequestVersionConflictException();
        if (request.getDeliveryMethod() != DeliveryMethod.ELECTRONIC) throw new DeliveryMethodMismatchException();
        if (issued.existsByRequestId(requestId)) throw new IssuedDocumentAlreadyExistsException();
        transitions.requireAction(RequestAction.UPLOAD_ISSUED_DOCUMENT, UserRole.STAFF,
                request.getStatus(), request.getDeliveryMethod());
        transitions.requireTransition(request.getStatus(), RequestStatus.READY, request.getDeliveryMethod());
        StoredFile pdf = storage.finalizeFiles(operation, requestId).getFirst();
        issued.saveAndFlush(new IssuedDocument(request, staff, pdf.originalFilename(),
                pdf.storedFilename(), pdf.relativePath(), pdf.contentType(), pdf.sizeBytes()));
        request.markElectronicReady(Instant.now(clock));
        request.updateStatus(RequestStatus.READY);
        requests.saveAndFlush(request);
        history.saveAndFlush(new RequestStatusHistory(request, RequestStatus.PROCESSING,
                RequestStatus.READY, staff, "Electronic document issued and ready."));
        notifications.create(new NotificationCommand(request.getStudent().getId(),
                NotificationType.ELECTRONIC_DOCUMENT_READY, "Electronic document ready",
                "Your electronic document for request " + request.getRequestNumber()
                        + " is ready to download from UniDoc.", "/requests/" + requestId));
    }
}
