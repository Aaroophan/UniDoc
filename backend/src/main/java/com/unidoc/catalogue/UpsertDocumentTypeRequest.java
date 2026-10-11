package com.unidoc.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Full replacement payload. Boolean wrappers make omitted flags a validation error. */
@JsonIgnoreProperties(ignoreUnknown = true)
@ValidDocumentTypeDetails
public record UpsertDocumentTypeRequest(
        @NotBlank(message = "Code is required.")
        @TrimmedSize(min = 2, max = 50, message = "Code must be between 2 and 50 characters.")
        @Pattern(regexp = "\\s*[A-Za-z0-9_-]{2,50}\\s*",
                message = "Code may contain only letters, numbers, underscores and hyphens.")
        String code,

        @NotBlank(message = "Name is required.")
        @NormalizedCatalogueText(min = 2, max = 150, message = "Name must be between 2 and 150 characters.")
        String name,

        @NotBlank(message = "Description is required.")
        @NormalizedCatalogueText(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters.")
        String description,

        @NotBlank(message = "Requirements are required.")
        @NormalizedCatalogueText(min = 3, max = 4000, message = "Requirements must be between 3 and 4000 characters.")
        String requirements,

        @NotNull(message = "Supporting-document setting is required.")
        Boolean supportingDocumentsRequired,

        @NotNull(message = "Electronic delivery setting is required.")
        Boolean electronicAvailable,

        @NotNull(message = "Physical delivery setting is required.")
        Boolean physicalAvailable,

        @NormalizedCatalogueText(max = 255, message = "Collection location must not exceed 255 characters.")
        String collectionLocation,

        @NormalizedCatalogueText(max = 2000, message = "Collection instructions must not exceed 2000 characters.")
        String collectionInstructions,

        @NotNull(message = "Active status is required.")
        Boolean active
) { }
