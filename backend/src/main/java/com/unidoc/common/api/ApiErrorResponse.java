package com.unidoc.common.api;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ApiErrorCode code,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors
) {
    public ApiErrorResponse {
        if (fieldErrors != null) {
            fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
        }
    }

    public static ApiErrorResponse of(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            String path,
            String requestId,
            Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                requestId,
                fieldErrors
        );
    }
}
