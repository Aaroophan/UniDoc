package com.unidoc.catalogue;

import java.time.Instant;

/** All editable values plus timestamps, without internal administrator IDs. */
public record AdminDocumentTypeResponse(
        long id,
        String code,
        String name,
        String description,
        String requirements,
        boolean supportingDocumentsRequired,
        boolean electronicAvailable,
        boolean physicalAvailable,
        String collectionLocation,
        String collectionInstructions,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) { }
