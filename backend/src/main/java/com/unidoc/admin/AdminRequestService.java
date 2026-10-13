package com.unidoc.admin;

import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.request.*;
import com.unidoc.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminRequestService {
    private final AdminAccess access;
    private final RequestReadQueries listings;
    private final DocumentRequestRepository requests;
    private final StudentProfileRepository profiles;
    private final RequestAttachmentRepository attachments;
    private final RequestStatusHistoryRepository histories;
    private final IssuedDocumentRepository issuedDocuments;

    public AdminRequestService(AdminAccess access, RequestReadQueries listings,
                               DocumentRequestRepository requests, StudentProfileRepository profiles,
                               RequestAttachmentRepository attachments, RequestStatusHistoryRepository histories,
                               IssuedDocumentRepository issuedDocuments) {
        this.access = access;
        this.listings = listings;
        this.requests = requests;
        this.profiles = profiles;
        this.attachments = attachments;
        this.histories = histories;
        this.issuedDocuments = issuedDocuments;
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> list(long userId, String q, String status, Long typeId,
                                              String delivery, LocalDate from, LocalDate to,
                                              LocalDate required, Integer page, Integer size,
                                              String sort, String direction) {
        access.requireActiveAdministrator(userId);
        return listings.list(q, status, typeId, delivery, from, to, required, page, size, sort, direction);
    }

    @Transactional(readOnly = true)
    public AdminRequestDetailResponse detail(long userId, long requestId) {
        access.requireActiveAdministrator(userId);
        if (requestId < 1) throw new DocumentRequestNotFoundException();
        DocumentRequest request = requests.findById(requestId).orElseThrow(DocumentRequestNotFoundException::new);
        User studentAccount = request.getStudent();
        StudentProfile profile = profiles.findByUserId(studentAccount.getId()).orElse(null);
        StaffRequestStudentSummary student = new StaffRequestStudentSummary(
                studentAccount.getId(), studentAccount.getFullName(), studentAccount.getEmail(),
                profile == null ? null : profile.getRegistrationNumber(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getFaculty(),
                profile == null ? null : profile.getDepartment(),
                profile == null ? null : profile.getDegreeProgramme(),
                profile == null ? null : profile.getAcademicYear());
        IssuedDocument issued = issuedDocuments.findByRequestId(requestId).orElse(null);
        return new AdminRequestDetailResponse(
                request.getId(), request.getRequestNumber(), student,
                request.getDocumentType().getId(), request.getDocumentTypeNameSnapshot(),
                request.getPurpose(), request.getRequiredDate(), request.getDeliveryMethod(), request.getStatus(),
                request.getAdditionalInformationMessage(), request.getRejectionReason(),
                request.getDeliveryMethod() == DeliveryMethod.PHYSICAL ? request.getCollectionLocationSnapshot() : null,
                request.getDeliveryMethod() == DeliveryMethod.PHYSICAL ? request.getCollectionInstructionsSnapshot() : null,
                request.getReadyAt(), request.getCompletedAt(), request.getCollectionDate(), request.getCollectionNote(),
                attachments.findByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                        .map(CommonDtoMapper::toAttachmentMetadata).toList(),
                issued == null ? null : IssuedDocumentMetadata.from(issued),
                histories.findByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                        .map(CommonDtoMapper::toHistoryEntry).toList(),
                List.of(), request.getVersion(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
