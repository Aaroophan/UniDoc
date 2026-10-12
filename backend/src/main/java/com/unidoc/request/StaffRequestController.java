package com.unidoc.request;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import com.unidoc.file.FileDownloadHeaders;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/staff/requests")
public class StaffRequestController {
    private final StaffRequestService service;
    private final CurrentUserProvider currentUser;

    public StaffRequestController(StaffRequestService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
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

    @GetMapping("/{requestId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable long requestId, @PathVariable long attachmentId) {
        PreparedAttachmentDownload prepared = service.downloadAttachment(
                currentUser.currentUserId(), requestId, attachmentId);
        byte[] bytes = prepared.bytes();
        return ResponseEntity.ok().headers(FileDownloadHeaders.forDownload(
                prepared.displayFilename(), prepared.contentType(), bytes.length)).body(bytes);
    }
}
