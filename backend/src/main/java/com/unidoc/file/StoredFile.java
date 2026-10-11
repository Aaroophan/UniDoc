package com.unidoc.file;

/** Metadata to persist on the caller's authorized request record; never a public DTO. */
public record StoredFile(
        String originalFilename,
        String storedFilename,
        String relativePath,
        String contentType,
        long sizeBytes
) {
}
