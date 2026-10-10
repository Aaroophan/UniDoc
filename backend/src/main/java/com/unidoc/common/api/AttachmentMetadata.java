package com.unidoc.common.api;

import com.unidoc.request.AttachmentKind;

import java.time.Instant;

public record AttachmentMetadata(
        Long id,
        AttachmentKind kind,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
}
