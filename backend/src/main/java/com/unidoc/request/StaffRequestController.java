package com.unidoc.request;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import com.unidoc.file.FileDownloadHeaders;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/staff/requests")
public class StaffRequestController {
    private final StaffRequestService service;
    private final CurrentUserProvider currentUser;
    private final ElectronicDocumentService electronic;

    public StaffRequestController(StaffRequestService service, CurrentUserProvider currentUser,
                                  ElectronicDocumentService electronic) {
        this.service = service;
        this.currentUser = currentUser;
        this.electronic = electronic;
    }

    @GetMapping
    public PageResponse<RequestSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return service.list(currentUser.currentUserId(), q, status, documentTypeId, deliveryMethod,
                submittedFrom, submittedTo, requiredDate, page, size, sort, direction);
    }

    @GetMapping("/{requestId}")
    public StaffRequestDetailResponse detail(@PathVariable long requestId) {
        return service.getDetail(currentUser.currentUserId(), requestId);
    }

    @PostMapping("/{requestId}/start-review")
    public StaffRequestDetailResponse startReview(@PathVariable long requestId,
                                                   @Valid @RequestBody StartReviewRequest request) {
        return service.startReview(currentUser.currentUserId(), requestId, request.expectedVersion());
    }


    @PostMapping("/{requestId}/request-information")
    public StaffRequestDetailResponse requestInformation(@PathVariable long requestId,
                                                          @Valid @RequestBody RequestInformationRequest request) {
        return service.requestInformation(currentUser.currentUserId(), requestId, request);
    }

    @PostMapping("/{requestId}/approve")
    public StaffRequestDetailResponse approve(@PathVariable long requestId,
                                               @Valid @RequestBody StartReviewRequest request) {
        return service.approve(currentUser.currentUserId(), requestId, request.expectedVersion());
    }

    @PostMapping("/{requestId}/reject")
    public StaffRequestDetailResponse reject(@PathVariable long requestId,
                                              @Valid @RequestBody RejectRequest request) {
        return service.reject(currentUser.currentUserId(), requestId, request);
    }

    @PostMapping("/{requestId}/start-processing")
    public StaffRequestDetailResponse startProcessing(@PathVariable long requestId,
                                                       @Valid @RequestBody StartReviewRequest request) {
        return service.startProcessing(currentUser.currentUserId(), requestId, request.expectedVersion());
    }

    @PostMapping("/{requestId}/ready-for-collection")
    public StaffRequestDetailResponse readyForCollection(@PathVariable long requestId,
                                                          @Valid @RequestBody StartReviewRequest request) {
        return service.readyForCollection(currentUser.currentUserId(), requestId, request.expectedVersion());
    }

    @PostMapping("/{requestId}/complete-collection")
    public StaffRequestDetailResponse completeCollection(@PathVariable long requestId,
                                                          @Valid @RequestBody CompleteCollectionRequest request) {
        return service.completeCollection(currentUser.currentUserId(), requestId, request);
    }

    @PostMapping(value = "/{requestId}/issued-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StaffRequestDetailResponse uploadIssuedDocument(@PathVariable long requestId,
            @RequestParam(required = false) Long expectedVersion,
            @RequestPart(name = "file", required = false) java.util.List<MultipartFile> files) {
        if (files != null && files.size() > 1) {
            throw new com.unidoc.common.exception.InvalidQueryParameterException(
                    "Exactly one issued PDF file is allowed.");
        }
        return electronic.upload(currentUser.currentUserId(), requestId, expectedVersion,
                files == null || files.isEmpty() ? null : files.getFirst());
    }

    @GetMapping("/{requestId}/issued-document")
    public ResponseEntity<byte[]> downloadIssuedDocument(@PathVariable long requestId) {
        PreparedAttachmentDownload prepared = electronic.downloadForStaff(currentUser.currentUserId(), requestId);
        byte[] bytes = prepared.bytes();
        return ResponseEntity.ok().headers(FileDownloadHeaders.forDownload(
                prepared.displayFilename(), prepared.contentType(), bytes.length)).body(bytes);
    }

    @GetMapping("/{requestId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable long requestId, @PathVariable long attachmentId) {
        PreparedAttachmentDownload prepared = service.downloadAttachment(
                currentUser.currentUserId(), requestId, attachmentId);
        byte[] bytes = prepared.bytes();
        return ResponseEntity.ok().headers(FileDownloadHeaders.forDownload(
                prepared.displayFilename(), prepared.contentType(), bytes.length)).body(bytes);
    }
}
