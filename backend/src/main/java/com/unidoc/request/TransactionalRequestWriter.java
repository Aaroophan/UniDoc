package com.unidoc.request;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.StoredFile;
import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationService;
import com.unidoc.notification.NotificationType;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.profile.StudentProfileService;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** A distinct proxied bean gives the outer coordinator visibility into commit-time failures. */
@Service
public class TransactionalRequestWriter {

    private final UserRepository users;
    private final StudentProfileService studentProfiles;
    private final StudentProfileRepository profileRecords;
    private final DocumentTypeRepository types;
    private final DocumentRequestRepository requests;
    private final RequestAttachmentRepository attachments;
    private final RequestStatusHistoryRepository history;
    private final NotificationService notifications;
    private final FileStorageService storage;
    private final RequestTransitionValidator transitions;
    private final RequestReviewTransitions reviewTransitions;
    private final Clock clock;

    public TransactionalRequestWriter(UserRepository users, StudentProfileService studentProfiles,
                                      StudentProfileRepository profileRecords, DocumentTypeRepository types,
                                      DocumentRequestRepository requests, RequestAttachmentRepository attachments,
                                      RequestStatusHistoryRepository history, NotificationService notifications,
                                      FileStorageService storage, RequestTransitionValidator transitions,
                                      RequestReviewTransitions reviewTransitions, Clock clock) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.profileRecords = profileRecords;
        this.types = types;
        this.requests = requests;
        this.attachments = attachments;
        this.history = history;
        this.notifications = notifications;
        this.storage = storage;
        this.transitions = transitions;
        this.reviewTransitions = reviewTransitions;
        this.clock = clock;
    }

    /** All rows (request, files, history and notifications) commit or roll back together. */
    @Transactional
    public StudentRequestDetailResponse create(long studentUserId, CreateDocumentRequest command,
                                                FileStorageService.StorageOperation files) {
        User student = users.findById(studentUserId).orElseThrow(SessionRevokedException::new);
        if (student.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can submit document requests.");
        }
        if (student.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
        studentProfiles.requireCompleteForSubmission(studentUserId);
        DocumentType type = DocumentRequestService.requireAvailableDocumentType(types, command.documentTypeId());
        DocumentRequestService.validateDateAndDelivery(command, type, clock);
        // Recheck required-attachments flag here in case the catalogue changed during staging.
        if (type.isSupportingDocumentsRequired() && (files == null || files.stagedCount() == 0)) {
            throw new com.unidoc.file.FileValidationException(
                    com.unidoc.common.api.ApiErrorCode.SUPPORTING_DOCUMENT_REQUIRED);
        }
        transitions.requireTransition(null, RequestStatus.SUBMITTED, command.deliveryMethod());

        DocumentRequest request = requests.saveAndFlush(new DocumentRequest(student, type,
                command.deliveryMethod(), command.purpose(), command.requiredDate()));
        request.assignRequestNumber();
        requests.flush(); // Request number must be persisted before metadata/history/notifications.

        List<RequestAttachment> savedFiles = new ArrayList<>();
        if (files != null) {
            for (StoredFile stored : storage.finalizeFiles(files, request.getId())) {
                savedFiles.add(new RequestAttachment(request, student, AttachmentKind.INITIAL_SUPPORT,
                        stored.originalFilename(), stored.storedFilename(), stored.relativePath(),
                        stored.contentType(), stored.sizeBytes()));
            }
            savedFiles = savedFiles.stream().map(attachments::saveAndFlush).toList();
        }

        RequestStatusHistory initial = history.saveAndFlush(
                new RequestStatusHistory(request, null, RequestStatus.SUBMITTED, student, "Request submitted."));
        String number = request.getRequestNumber();
        notifications.create(new NotificationCommand(student.getId(), NotificationType.REQUEST_SUBMITTED,
                "Request submitted", "Your document request " + number + " has been submitted.",
                "/requests/" + request.getId()));

        // Paginate active staff; no arbitrary upper bound on how many must be notified.
        int page = 0;
        Page<User> staff;
        do {
            staff = users.findByRoleAndStatus(UserRole.STAFF, UserStatus.ACTIVE,
                    PageRequest.of(page++, 100, Sort.by(Sort.Direction.ASC, "id")));
            for (User recipient : staff.getContent()) {
                notifications.create(new NotificationCommand(recipient.getId(), NotificationType.REQUEST_SUBMITTED,
                        "New document request", "Document request " + number + " is awaiting review.",
                        "/staff/requests/" + request.getId()));
            }
        } while (staff.hasNext());

        String registration = profileRecords.findByUserId(studentUserId)
                .map(profile -> profile.getRegistrationNumber()).orElse(null);
        return DocumentRequestService.toDetail(request, registration, savedFiles, List.of(initial), null, transitions);
    }
    /** Called through the Spring proxy: commit finishes before the outer storage coordinator returns. */
    @Transactional
    public StudentRequestDetailResponse submitInformationResponse(long studentUserId, long requestId,
                                                                   InformationResponseRequest command,
                                                                   FileStorageService.StorageOperation files) {
        User student = users.findById(studentUserId).orElseThrow(SessionRevokedException::new);
        if (student.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can answer an information request.");
        }
        if (student.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
        if (requestId < 1) throw new DocumentRequestNotFoundException();
        DocumentRequest request = requests.findByIdAndStudentId(requestId, studentUserId)
                .orElseThrow(DocumentRequestNotFoundException::new);
        long version = command.expectedVersion() == null ? request.getVersion() : command.expectedVersion();
        if (request.getVersion() != version) throw new RequestVersionConflictException();
        transitions.requireAction(RequestAction.RESPOND, student.getRole(), request.getStatus(), request.getDeliveryMethod());

        List<RequestAttachment> newFiles = new ArrayList<>();
        if (files != null) {
            for (StoredFile stored : storage.finalizeFiles(files, request.getId())) {
                newFiles.add(attachments.saveAndFlush(new RequestAttachment(request, student,
                        AttachmentKind.ADDITIONAL_INFORMATION, stored.originalFilename(),
                        stored.storedFilename(), stored.relativePath(), stored.contentType(), stored.sizeBytes())));
            }
        }
        String number = request.getRequestNumber();
        String note = command.message().strip();
        List<NotificationCommand> notices = new ArrayList<>();
        notices.add(new NotificationCommand(studentUserId, NotificationType.ADDITIONAL_INFORMATION_SUBMITTED,
                "Information response submitted", "Your response for document request " + number + " was submitted.",
                "/requests/" + requestId));
        int page = 0;
        Page<User> staff;
        do {
            staff = users.findByRoleAndStatus(UserRole.STAFF, UserStatus.ACTIVE,
                    PageRequest.of(page++, 100, Sort.by(Sort.Direction.ASC, "id")));
            for (User recipient : staff.getContent()) {
                notices.add(new NotificationCommand(recipient.getId(),
                        NotificationType.ADDITIONAL_INFORMATION_SUBMITTED,
                        "Additional information submitted", "Document request " + number + " is ready for review again.",
                        "/staff/requests/" + requestId));
            }
        } while (staff.hasNext());
        reviewTransitions.record(request, student, version, RequestAction.RESPOND, note,
                DocumentRequest::clearAdditionalInformation, notices);
        List<RequestAttachment> allFiles = attachments.findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        List<RequestStatusHistory> events = history.findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        String registration = profileRecords.findByUserId(studentUserId)
                .map(profile -> profile.getRegistrationNumber()).orElse(null);
        return DocumentRequestService.toDetail(request, registration, allFiles, events, null, transitions);
    }

}
