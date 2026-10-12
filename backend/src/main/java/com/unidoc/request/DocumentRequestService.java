package com.unidoc.request;

import com.unidoc.auth.InactiveAccountException;
import com.unidoc.auth.SessionRevokedException;
import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeInactiveException;
import com.unidoc.catalogue.DocumentTypeNotFoundException;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.file.AttachmentNotFoundException;
import com.unidoc.file.FileStorageService;
import com.unidoc.file.PreparedFileDownload;
import com.unidoc.file.StorageCategory;
import com.unidoc.file.ValidatedUpload;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.profile.StudentProfileService;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Student request API and nontransactional submission coordinator. The outer create method
 * deliberately owns the storage operation and surrounds the writer's FULL transaction,
 * including its commit: an exception at commit must still compensate finalized bytes.
 */
@Service
public class DocumentRequestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRequestService.class);

    private final UserRepository users;
    private final StudentProfileService studentProfiles;
    private final StudentProfileRepository profileRecords;
    private final DocumentTypeRepository documentTypes;
    private final DocumentRequestRepository requests;
    private final RequestAttachmentRepository attachments;
    private final RequestStatusHistoryRepository history;
    private final IssuedDocumentRepository issuedDocuments;
    private final RequestTransitionValidator transitions;
    private final FileStorageService storage;
    private final TransactionalRequestWriter writer;
    private final Validator validator;
    private final Clock clock;

    public DocumentRequestService(UserRepository users, StudentProfileService studentProfiles,
                                  StudentProfileRepository profileRecords, DocumentTypeRepository documentTypes,
                                  DocumentRequestRepository requests, RequestAttachmentRepository attachments,
                                  RequestStatusHistoryRepository history, IssuedDocumentRepository issuedDocuments,
                                  RequestTransitionValidator transitions, FileStorageService storage,
                                  TransactionalRequestWriter writer, Validator validator, Clock clock) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.profileRecords = profileRecords;
        this.documentTypes = documentTypes;
        this.requests = requests;
        this.attachments = attachments;
        this.history = history;
        this.issuedDocuments = issuedDocuments;
        this.transitions = transitions;
        this.storage = storage;
        this.writer = writer;
        this.validator = validator;
        this.clock = clock;
    }

    /** No @Transactional here. Transaction commit takes place inside writer.create(), before returning. */
    public StudentRequestDetailResponse create(long studentUserId, CreateDocumentRequest command,
                                               List<MultipartFile> suppliedFiles) {
        Objects.requireNonNull(command, "Request fields are required.");
        Set<ConstraintViolation<CreateDocumentRequest>> violations = validator.validate(command);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        requireActiveStudent(studentUserId);
        studentProfiles.requireCompleteForSubmission(studentUserId);
        DocumentType type = requireAvailableDocumentType(command.documentTypeId());
        validateDateAndDelivery(command, type, clock);
        List<ValidatedUpload> checked = storage.validateSupportingFiles(suppliedFiles,
                type.isSupportingDocumentsRequired());

        FileStorageService.StorageOperation operation = checked.isEmpty() ? null
                : storage.stage(checked, StorageCategory.SUPPORTING);
        StudentRequestDetailResponse created;
        try {
            // The Spring proxy completes commit before control returns to this try block.
            created = writer.create(studentUserId, command, operation);
        } catch (RuntimeException | Error failure) {
            if (operation != null) storage.cleanup(operation);
            throw failure;
        }
        log.info("Document request submitted requestId={} studentUserId={} attachmentCount={}",
                created.id(), studentUserId, created.attachments().size());
        return created;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentRequestSummary> list(long studentUserId, String rawStatus,
                                                    String rawDeliveryMethod, Integer page, Integer size,
                                                    String sort, String direction) {
        requireActiveStudent(studentUserId);
        RequestStatus status = QueryParameters.enumValue(rawStatus, RequestStatus.class, "status");
        DeliveryMethod deliveryMethod = QueryParameters.enumValue(rawDeliveryMethod, DeliveryMethod.class,
                "deliveryMethod");
        Pageable pageable = QueryParameters.pageable(page, size, sort, direction,
                DocumentRequestSortProperties.MAPPINGS, DocumentRequestSortProperties.DEFAULT,
                Sort.Direction.DESC);
        Page<DocumentRequest> results = requests.findAll(
                DocumentRequestSpecifications.forStudent(studentUserId, status, deliveryMethod), pageable);
        return PageResponse.from(results, DocumentRequestService::toSummary);
    }

    @Transactional(readOnly = true)
    public StudentRequestDetailResponse getDetail(long studentUserId, long requestId) {
        requireActiveStudent(studentUserId);
        DocumentRequest request = ownRequest(studentUserId, requestId);
        String registration = profileRecords.findByUserId(studentUserId)
                .map(profile -> profile.getRegistrationNumber()).orElse(null);
        List<RequestAttachment> files = attachments.findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        List<RequestStatusHistory> events = history.findByRequestIdOrderByCreatedAtAscIdAsc(requestId);
        IssuedDocument issued = request.getDeliveryMethod() == DeliveryMethod.ELECTRONIC
                && (request.getStatus() == RequestStatus.READY || request.getStatus() == RequestStatus.COMPLETED)
                ? issuedDocuments.findByRequestId(requestId).orElse(null) : null;
        return toDetail(request, registration, files, events, issued, transitions);
    }

    @Transactional(readOnly = true)
    public PreparedAttachmentDownload downloadAttachment(long studentUserId, long requestId, long attachmentId) {
        requireActiveStudent(studentUserId);
        ownRequest(studentUserId, requestId); // Hide the existence of other students' requests.
        if (attachmentId < 1) throw new AttachmentNotFoundException();
        RequestAttachment attachment = attachments.findByIdAndRequestId(attachmentId, requestId)
                .orElseThrow(AttachmentNotFoundException::new);
        PreparedFileDownload file = storage.readAuthorized(
                attachment.getRelativePath(), attachment.getContentType(), attachment.getSizeBytes());
        return new PreparedAttachmentDownload(file.bytes(), attachment.getOriginalFilename(), file.contentType());
    }

    private DocumentRequest ownRequest(long userId, long id) {
        if (id < 1) throw new DocumentRequestNotFoundException();
        return requests.findByIdAndStudentId(id, userId).orElseThrow(DocumentRequestNotFoundException::new);
    }

    private void requireActiveStudent(long id) {
        User account = users.findById(id).orElseThrow(SessionRevokedException::new);
        if (account.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Student request operations require a student account.");
        }
        if (account.getStatus() != UserStatus.ACTIVE) throw new InactiveAccountException();
    }

    static DocumentType requireAvailableDocumentType(DocumentTypeRepository catalogue, long typeId) {
        DocumentType type = catalogue.findById(typeId).orElseThrow(DocumentTypeNotFoundException::new);
        if (!type.isActive()) throw new DocumentTypeInactiveException();
        return type;
    }

    private DocumentType requireAvailableDocumentType(long typeId) {
        return requireAvailableDocumentType(documentTypes, typeId);
    }

    static void validateDateAndDelivery(CreateDocumentRequest command, DocumentType type, Clock clock) {
        // UniDoc runs locally: the host's calendar zone is also the user's calendar zone.
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        if (command.requiredDate().isBefore(today)) throw new InvalidRequiredDateException();
        boolean offered = switch (command.deliveryMethod()) {
            case ELECTRONIC -> type.isElectronicAvailable();
            case PHYSICAL -> type.isPhysicalAvailable();
        };
        if (!offered) throw new InvalidDeliveryMethodException();
    }

    static StudentRequestSummary toSummary(DocumentRequest request) {
        return new StudentRequestSummary(
                request.getId(), request.getRequestNumber(), request.getDocumentType().getId(),
                request.getDocumentTypeNameSnapshot(), request.getDeliveryMethod(), request.getStatus(),
                request.getRequiredDate(), request.getCreatedAt(), request.getUpdatedAt(), request.getVersion());
    }

    static StudentRequestDetailResponse toDetail(DocumentRequest request, String registration,
                                                 List<RequestAttachment> files, List<RequestStatusHistory> events,
                                                 IssuedDocument issued, RequestTransitionValidator transitions) {
        User owner = request.getStudent();
        return new StudentRequestDetailResponse(
                request.getId(), request.getRequestNumber(),
                new StudentRequestStudentSummary(owner.getId(), owner.getFullName(), owner.getEmail(), registration),
                request.getDocumentType().getId(), request.getDocumentTypeNameSnapshot(), request.getPurpose(),
                request.getRequiredDate(), request.getDeliveryMethod(), request.getStatus(),
                request.getStatus() == RequestStatus.ADDITIONAL_INFORMATION_REQUIRED
                        ? request.getAdditionalInformationMessage() : null,
                request.getStatus() == RequestStatus.REJECTED ? request.getRejectionReason() : null,
                request.getDeliveryMethod() == DeliveryMethod.PHYSICAL
                        ? request.getCollectionLocationSnapshot() : null,
                request.getDeliveryMethod() == DeliveryMethod.PHYSICAL
                        ? request.getCollectionInstructionsSnapshot() : null,
                request.getReadyAt(), request.getCompletedAt(), request.getCollectionDate(), request.getCollectionNote(),
                files.stream().map(CommonDtoMapper::toAttachmentMetadata).toList(),
                issued == null ? null : IssuedDocumentMetadata.from(issued),
                events.stream().map(CommonDtoMapper::toHistoryEntry).toList(),
                transitions.availableActions(UserRole.STUDENT, request.getStatus(), request.getDeliveryMethod()),
                request.getVersion(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
