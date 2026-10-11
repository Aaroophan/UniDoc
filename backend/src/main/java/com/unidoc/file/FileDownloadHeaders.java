package com.unidoc.file;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Use after ownership checks and file preparation, never build raw header strings. */
public final class FileDownloadHeaders {

    private static final Set<String> APPROVED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private FileDownloadHeaders() {
    }

    public static HttpHeaders forDownload(String displayFilename, String validatedContentType, long sizeBytes) {
        if (!APPROVED_TYPES.contains(validatedContentType) || sizeBytes < 0) {
            throw new IllegalArgumentException("Invalid file response metadata.");
        }
        String fallback = switch (validatedContentType) {
            case "application/pdf" -> "document.pdf";
            case "image/jpeg" -> "attachment.jpg";
            default -> "attachment.png";
        };
        String safeName = SanitizedFileNames.sanitize(displayFilename, fallback);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(validatedContentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(safeName, StandardCharsets.UTF_8).build());
        headers.setContentLength(sizeBytes);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, no-store");
        headers.setPragma("no-cache");
        return headers;
    }
}
