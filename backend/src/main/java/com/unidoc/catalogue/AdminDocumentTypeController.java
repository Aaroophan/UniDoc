package com.unidoc.catalogue;

import com.unidoc.auth.CurrentUserProvider;
import com.unidoc.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/document-types")
public class AdminDocumentTypeController {
    private final DocumentCatalogueService catalogue;
    private final CurrentUserProvider currentUser;

    public AdminDocumentTypeController(DocumentCatalogueService catalogue, CurrentUserProvider currentUser) {
        this.catalogue = catalogue;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<AdminDocumentTypeResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return catalogue.listManaged(currentUser.currentUserId(), q, active, page, size, sort, direction);
    }

    @PostMapping
    public ResponseEntity<AdminDocumentTypeResponse> create(@Valid @RequestBody UpsertDocumentTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogue.create(currentUser.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public AdminDocumentTypeResponse detail(@PathVariable @Positive long id) {
        return catalogue.getManaged(currentUser.currentUserId(), id);
    }

    @PutMapping("/{id}")
    public AdminDocumentTypeResponse update(@PathVariable @Positive long id,
                                            @Valid @RequestBody UpsertDocumentTypeRequest request) {
        return catalogue.update(currentUser.currentUserId(), id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminDocumentTypeResponse status(@PathVariable @Positive long id,
                                            @Valid @RequestBody ChangeDocumentTypeStatusRequest request) {
        return catalogue.changeStatus(currentUser.currentUserId(), id, request);
    }
}
