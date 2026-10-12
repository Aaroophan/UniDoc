package com.unidoc.request;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import com.unidoc.file.FileDownloadHeaders;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

/** Student-only endpoint family, additionally enforcing ownership in DocumentRequestService. */
@RestController
@RequestMapping("/api/student/requests")
public class StudentRequestController {

    private final CurrentUserProvider currentUser;
    private final DocumentRequestService service;

    public StudentRequestController(CurrentUserProvider currentUser, DocumentRequestService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudentRequestDetailResponse> create(
            @Valid @RequestPart("request") CreateDocumentRequest command,
            @RequestPart(name = "files", required = false) List<MultipartFile> files
    ) {
        StudentRequestDetailResponse created = service.create(currentUser.currentUserId(), command, files);
        return ResponseEntity.created(URI.create("/api/student/requests/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<StudentRequestSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return service.list(currentUser.currentUserId(), status, deliveryMethod, page, size, sort, direction);
    }

    @GetMapping("/{requestId}")
    public StudentRequestDetailResponse detail(@PathVariable long requestId) {
        return service.getDetail(currentUser.currentUserId(), requestId);
    }

    @GetMapping("/{requestId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable long requestId, @PathVariable long attachmentId) {
        PreparedAttachmentDownload prepared = service.downloadAttachment(
                currentUser.currentUserId(), requestId, attachmentId);
        byte[] bytes = prepared.bytes();
        return ResponseEntity.ok()
                .headers(FileDownloadHeaders.forDownload(prepared.displayFilename(), prepared.contentType(), bytes.length))
                .body(bytes);
    }
}
