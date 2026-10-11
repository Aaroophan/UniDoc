package com.unidoc.file;

import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/** Immutable, server-validated metadata; source is only used while staging this request. */
public record ValidatedUpload(
        MultipartFile source,
        String originalFilename,
        String storedExtension,
        String contentType,
        long sizeBytes
) {
    public ValidatedUpload {
        Objects.requireNonNull(source);
        Objects.requireNonNull(originalFilename);
        Objects.requireNonNull(storedExtension);
        Objects.requireNonNull(contentType);
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("File size must be positive.");
        }
    }
}
