package com.unidoc.catalogue;

import com.unidoc.auth.CurrentUserProvider;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/document-types")
public class DocumentTypeController {
    private final DocumentCatalogueService catalogue;
    private final CurrentUserProvider currentUser;

    public DocumentTypeController(DocumentCatalogueService catalogue, CurrentUserProvider currentUser) {
        this.catalogue = catalogue;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CatalogueDocumentTypeResponse> list() {
        return catalogue.listActive(currentUser.currentUserId());
    }

    @GetMapping("/{id}")
    public CatalogueDocumentTypeResponse detail(@PathVariable @Positive long id) {
        return catalogue.getActive(currentUser.currentUserId(), id);
    }
}
