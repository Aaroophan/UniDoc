package com.unidoc.request;

import java.time.Instant;

public record IssuedDocumentMetadata(
        String originalFilename,
        long sizeBytes,
        int downloadCount,
        Instant firstDownloadedAt,
        Instant createdAt
) {
    public static IssuedDocumentMetadata from(IssuedDocument document) {
        return new IssuedDocumentMetadata(document.getOriginalFilename(), document.getSizeBytes(),
                document.getDownloadCount(), document.getFirstDownloadedAt(), document.getCreatedAt());
    }
}
