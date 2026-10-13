package com.unidoc.admin;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/admin/reports")
public class RequestReportController {
    private final RequestReportService service;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public RequestReportController(RequestReportService service, CurrentUserProvider currentUser, Clock clock) {
        this.service = service;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/requests")
    public PageResponse<RequestReportRow> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var filter = RequestReportFilter.parse(submittedFrom, submittedTo, status, documentTypeId, deliveryMethod);
        return service.report(currentUser.currentUserId(), filter, page, size, sort, direction);
    }

    @GetMapping("/requests.csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) String deliveryMethod,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var filter = RequestReportFilter.parse(submittedFrom, submittedTo, status, documentTypeId, deliveryMethod);
        byte[] bytes = service.csv(currentUser.currentUserId(), filter, sort, direction);
        String filename = "unidoc-request-report-" + LocalDate.now(clock.withZone(ZoneId.systemDefault())) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(bytes.length).body(bytes);
    }
}
