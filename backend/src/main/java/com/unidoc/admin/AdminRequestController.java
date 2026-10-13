package com.unidoc.admin;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.RequestSummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** Read-only monitoring routes. No staff processing or download endpoint exists here. */
@RestController
@RequestMapping("/api/admin/requests")
public class AdminRequestController {
    private final AdminRequestService service;
    private final CurrentUserProvider currentUser;

    public AdminRequestController(AdminRequestService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<RequestSummary> list(
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long documentTypeId, @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredDate,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        return service.list(currentUser.currentUserId(), q, status, documentTypeId, deliveryMethod,
                submittedFrom, submittedTo, requiredDate, page, size, sort, direction);
    }

    @GetMapping("/{requestId}")
    public AdminRequestDetailResponse detail(@PathVariable long requestId) {
        return service.detail(currentUser.currentUserId(), requestId);
    }
}
