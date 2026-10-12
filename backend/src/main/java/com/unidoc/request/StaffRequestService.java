package com.unidoc.request;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.file.AttachmentNotFoundException;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.PreparedFileDownload;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared staff work list, read-only detail and first workflow command. */
@Service
public class StaffRequestService {
    private static final Logger log = LoggerFactory.getLogger(StaffRequestService.class);

    private final UserRepository users;
    private final DocumentRequestRepository requests;
    private final StudentProfileRepository profiles;
    private final RequestAttachmentRepository attachments;
    private final RequestStatusHistoryRepository histories;
    private final IssuedDocumentRepository issuedDocuments;
    private final FileStorageService storage;
    private final RequestTransitionValidator transitions;

    public StaffRequestService(UserRepository users, DocumentRequestRepository requests,
                               StudentProfileRepository profiles, RequestAttachmentRepository attachments,
                               RequestStatusHistoryRepository histories, IssuedDocumentRepository issuedDocuments,
                               FileStorageService storage, RequestTransitionValidator transitions) {
        this.users = users;
        this.requests = requests;
        this.profiles = profiles;
        this.attachments = attachments;
        this.histories = histories;
        this.issuedDocuments = issuedDocuments;
        this.storage = storage;
        this.transitions = transitions;
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> list(long staffUserId, String q, String rawStatus,
                                              Long documentTypeId, String rawDeliveryMethod,
                                              LocalDate submittedFrom, LocalDate submittedTo,
                                              LocalDate requiredDate, Integer page, Integer size,
                                              String sort, String direction) {
        requireActiveStaff(staffUserId);
        if (documentTypeId != null && documentTypeId < 1) {
            throw new com.unidoc.common.exception.InvalidQueryParameterException(
                    "documentTypeId must be positive.");
        }
        QueryParameters.validateDateRange(submittedFrom, submittedTo);
        String queryText = QueryParameters.freeText(q, 120);
        RequestStatus status = QueryParameters.enumValue(rawStatus, RequestStatus.class, "status");
        DeliveryMethod method = QueryParameters.enumValue(rawDeliveryMethod, DeliveryMethod.class, "deliveryMethod");
        Pageable pageable = QueryParameters.pageable(page, size, sort, direction,
                DocumentRequestSortProperties.MAPPINGS, DocumentRequestSortProperties.DEFAULT, Sort.Direction.DESC);
        // Stable ID tie-breaker when several requests share the same sort value.
        pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(pageable.getSort().iterator().next().getDirection(), "id")));
        Page<DocumentRequest> matched = requests.findAll(DocumentRequestSpecifications.forStaffOrAdmin(
                queryText, status, documentTypeId, method, submittedFrom, submittedTo, requiredDate), pageable);

        // Registration number is optional and queried once per page, not once per result row.
        Set<Long> studentIds = matched.getContent().stream()
                .map(request -> request.getStudent().getId()).collect(Collectors.toSet());
        Map<Long, String> registrations = studentIds.isEmpty() ? Map.of()
                : profiles.findByUserIdIn(studentIds).stream().collect(Collectors.toMap(
                        profile -> profile.getUser().getId(), StudentProfile::getRegistrationNumber,
                        (first, ignored) -> first));
        return PageResponse.from(matched, request -> CommonDtoMapper.toRequestSummary(
                request, registrations.get(request.getStudent().getId())));
    }

    @Transactional(readOnly = true)
    public StaffRequestDetailResponse getDetail(long staffUserId, long requestId) {
        requireActiveStaff(staffUserId);
        return assembleDetail(requireRequest(requestId));
    }

    @Transactional
    public StaffRequestDetailResponse startReview(long staffUserId, long requestId, long expectedVersion) {
        User actor = requireActiveStaff(staffUserId);
        DocumentRequest request = requireRequest(requestId);
        if (expectedVersion < 0 || request.getVersion() != expectedVersion) {
            throw new RequestVersionConflictException();
        }
        transitions.requireAction(RequestAction.START_REVIEW, actor.getRole(),
                request.getStatus(), request.getDeliveryMethod());
        transitions.requireTransition(request.getStatus(), RequestStatus.UNDER_REVIEW, request.getDeliveryMethod());
        RequestStatus previous = request.getStatus();
        request.updateStatus(RequestStatus.UNDER_REVIEW);
        // Force a version-checked SQL update before appending a single history entry.
        requests.saveAndFlush(request);
        histories.saveAndFlush(new RequestStatusHistory(
                request, previous, RequestStatus.UNDER_REVIEW, actor, "Review started."));
        StaffRequestDetailResponse result = assembleDetail(request);
        log.info("Staff review started requestId={} staffUserId={} version={}",
                requestId, staffUserId, result.version());
        // Deliberately no notification for the entry into review.
        return result;
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
