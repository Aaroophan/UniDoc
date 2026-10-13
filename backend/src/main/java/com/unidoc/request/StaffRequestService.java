package com.unidoc.request;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import com.unidoc.file.AttachmentNotFoundException;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.PreparedFileDownload;
import com.unidoc.notification.NotificationCommand;
import com.unidoc.notification.NotificationType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Shared staff work list, detail and explicit, versioned review commands. */
@Service
public class StaffRequestService {
    private static final Logger log = LoggerFactory.getLogger(StaffRequestService.class);

    private final UserRepository users;
    private final DocumentRequestRepository requests;
    private final RequestReadQueries listings;
    private final StudentProfileRepository profiles;
    private final RequestAttachmentRepository attachments;
    private final RequestStatusHistoryRepository histories;
    private final IssuedDocumentRepository issuedDocuments;
    private final FileStorageService storage;
    private final RequestTransitionValidator transitions;
    private final RequestReviewTransitions reviewTransitions;
    private final Validator validator;
    private final Clock clock;

    public StaffRequestService(UserRepository users, DocumentRequestRepository requests,
                               StudentProfileRepository profiles, RequestAttachmentRepository attachments,
                               RequestStatusHistoryRepository histories, IssuedDocumentRepository issuedDocuments,
                               FileStorageService storage, RequestTransitionValidator transitions,
                               RequestReviewTransitions reviewTransitions, Validator validator, Clock clock,
                               RequestReadQueries listings) {
        this.users = users;
        this.requests = requests;
        this.listings = listings;
        this.profiles = profiles;
        this.attachments = attachments;
        this.histories = histories;
        this.issuedDocuments = issuedDocuments;
        this.storage = storage;
        this.transitions = transitions;
        this.reviewTransitions = reviewTransitions;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> list(long staffUserId, String q, String rawStatus,
                                              Long documentTypeId, String rawDeliveryMethod,
                                              LocalDate submittedFrom, LocalDate submittedTo,
                                              LocalDate requiredDate, Integer page, Integer size,
                                              String sort, String direction) {
        requireActiveStaff(staffUserId);
        return listings.list(q, rawStatus, documentTypeId, rawDeliveryMethod, submittedFrom,
                submittedTo, requiredDate, page, size, sort, direction);
    }

    @Transactional(readOnly = true)
    public StaffRequestDetailResponse getDetail(long staffUserId, long requestId) {
        requireActiveStaff(staffUserId);
        return assembleDetail(requireRequest(requestId));
    }

    @Transactional
    public StaffRequestDetailResponse startReview(long staffUserId, long requestId, long expectedVersion) {
        return execute(staffUserId, requestId, expectedVersion, RequestAction.START_REVIEW,
                "Review started.", ignored -> {}, null, null, null);
    }

    @Transactional
    public StaffRequestDetailResponse requestInformation(long staffUserId, long requestId,
                                                         RequestInformationRequest command) {
        validate(command);
        String message = command.message().strip();
        return execute(staffUserId, requestId, command.expectedVersion(), RequestAction.REQUEST_INFORMATION,
                message, request -> request.requestAdditionalInformation(message),
                NotificationType.ADDITIONAL_INFORMATION_REQUIRED, "Additional information required",
                "Staff need more information to review your document request.");
    }

    @Transactional
    public StaffRequestDetailResponse approve(long staffUserId, long requestId, long expectedVersion) {
        return execute(staffUserId, requestId, expectedVersion, RequestAction.APPROVE,
                "Request approved.", ignored -> {}, NotificationType.REQUEST_APPROVED,
                "Request approved", "Your document request has been approved.");
    }

    @Transactional
    public StaffRequestDetailResponse reject(long staffUserId, long requestId, RejectRequest command) {
        validate(command);
        String reason = command.reason().strip();
        return execute(staffUserId, requestId, command.expectedVersion(), RequestAction.REJECT,
                reason, request -> request.reject(reason), NotificationType.REQUEST_REJECTED,
                "Request rejected", "Your document request was rejected. View the reason in UniDoc.");
    }

    @Transactional
    public StaffRequestDetailResponse startProcessing(long staffUserId, long requestId, long expectedVersion) {
        return execute(staffUserId, requestId, expectedVersion, RequestAction.START_PROCESSING,
                "Processing started.", ignored -> {}, NotificationType.PROCESSING_STARTED,
                "Processing started", "Your approved document request is now being processed.");
    }

    @Transactional
    public StaffRequestDetailResponse readyForCollection(long staffUserId, long requestId, long expectedVersion) {
        return execute(staffUserId, requestId, expectedVersion, RequestAction.READY_FOR_COLLECTION,
                "Physical document ready for collection.", request -> request.markPhysicalReady(Instant.now(clock)),
                NotificationType.PHYSICAL_DOCUMENT_READY, "Physical document ready for collection",
                "Your physical document is ready for collection. View your request for the collection location and instructions.");
    }

    @Transactional
    public StaffRequestDetailResponse completeCollection(long staffUserId, long requestId,
                                                         CompleteCollectionRequest command) {
        validate(command);
        String note = command.note() == null || command.note().isBlank() ? null : command.note().strip();
        return execute(staffUserId, requestId, command.expectedVersion(), RequestAction.COMPLETE_COLLECTION,
                note == null ? "Physical document collected." : note,
                request -> {
                    // Match request submission's laptop-local business-date convention.
                    if (command.collectionDate().isAfter(LocalDate.now(clock.withZone(ZoneId.systemDefault())))) {
                        throw new InvalidCollectionDateException();
                    }
                    request.recordPhysicalCollection(command.collectionDate(), note, Instant.now(clock));
                }, NotificationType.REQUEST_COMPLETED, "Document request completed",
                "Your physical document collection has been recorded and your request is completed.");
    }

    /** Called only from public transactional use cases above. */
    private StaffRequestDetailResponse execute(long staffUserId, long requestId, long expectedVersion,
                                               RequestAction action, String note, Consumer<DocumentRequest> changes,
                                               NotificationType notificationType, String title, String message) {
        User actor = requireActiveStaff(staffUserId);
        DocumentRequest request = requireRequest(requestId);
        List<NotificationCommand> notices = notificationType == null ? List.of() : List.of(
                new NotificationCommand(request.getStudent().getId(), notificationType,
                        title, message + " Reference: " + request.getRequestNumber(),
                        "/requests/" + requestId));
        reviewTransitions.record(request, actor, expectedVersion, action, note, changes, notices);
        StaffRequestDetailResponse detail = assembleDetail(request);
        log.info("Staff request action completed requestId={} staffUserId={} action={} version={}",
                requestId, staffUserId, action, detail.version());
        return detail;
    }

    private <T> void validate(T command) {
        Set<ConstraintViolation<T>> violations = validator.validate(java.util.Objects.requireNonNull(command));
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }

    @Transactional(readOnly = true)
    public PreparedAttachmentDownload downloadAttachment(long staffUserId, long requestId, long attachmentId) {
        requireActiveStaff(staffUserId);
        requireRequest(requestId);
        if (attachmentId < 1) throw new AttachmentNotFoundException();
        RequestAttachment attachment = attachments.findByIdAndRequestId(attachmentId, requestId)
                .orElseThrow(AttachmentNotFoundException::new);
        PreparedFileDownload file = storage.readAuthorized(
                attachment.getRelativePath(), attachment.getContentType(), attachment.getSizeBytes());
        log.info("Staff attachment accessed requestId={} attachmentId={} staffUserId={} role=STAFF",
                requestId, attachmentId, staffUserId);
        return new PreparedAttachmentDownload(file.bytes(), attachment.getOriginalFilename(), file.contentType());
    }

    private StaffRequestDetailResponse assembleDetail(DocumentRequest request) {
        User owner = request.getStudent();
        StudentProfile profile = profiles.findByUserId(owner.getId()).orElse(null);
        StaffRequestStudentSummary student = new StaffRequestStudentSummary(
                owner.getId(), owner.getFullName(), owner.getEmail(),
                profile == null ? null : profile.getRegistrationNumber(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getFaculty(),
                profile == null ? null : profile.getDepartment(),
                profile == null ? null : profile.getDegreeProgramme(),
                profile == null ? null : profile.getAcademicYear());
        List<RequestAttachment> files = attachments.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId());
        List<RequestStatusHistory> timeline = histories.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId());
        IssuedDocument issued = issuedDocuments.findByRequestId(request.getId()).orElse(null);
        return new StaffRequestDetailResponse(
                request.getId(), request.getRequestNumber(), student,
                request.getDocumentType().getId(), request.getDocumentTypeNameSnapshot(),
                request.getPurpose(), request.getRequiredDate(), request.getDeliveryMethod(), request.getStatus(),
                request.getAdditionalInformationMessage(), request.getRejectionReason(),
                request.getDeliveryMethod() == DeliveryMethod.PHYSICAL ? request.getCollectionLocationSnapshot() : null,
                request.getDeliveryMethod() == DeliveryMethod.PHYSICAL ? request.getCollectionInstructionsSnapshot() : null,
                request.getReadyAt(), request.getCompletedAt(), request.getCollectionDate(), request.getCollectionNote(),
                files.stream().map(CommonDtoMapper::toAttachmentMetadata).toList(),
                issued == null ? null : IssuedDocumentMetadata.from(issued),
                timeline.stream().map(CommonDtoMapper::toHistoryEntry).toList(),
                transitions.availableActions(UserRole.STAFF, request.getStatus(), request.getDeliveryMethod()),
                request.getVersion(), request.getCreatedAt(), request.getUpdatedAt());
    }

    private DocumentRequest requireRequest(long requestId) {
        if (requestId < 1) throw new DocumentRequestNotFoundException();
        return requests.findById(requestId).orElseThrow(DocumentRequestNotFoundException::new);
    }

    private User requireActiveStaff(long staffUserId) {
        User account = users.findById(staffUserId).orElseThrow(SessionRevokedException::new);
        if (account.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
        if (account.getRole() != UserRole.STAFF) {
            throw new AccessDeniedException("Only staff can process document requests.");
        }
        return account;
    }
}
