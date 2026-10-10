package com.unidoc.file;

import com.unidoc.common.api.ApiErrorCode;

import java.util.EnumSet;
import java.util.Map;

public class FileValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final EnumSet<ApiErrorCode> ALLOWED_CODES = EnumSet.of(
            ApiErrorCode.FILE_EMPTY,
            ApiErrorCode.FILE_TOO_LARGE,
            ApiErrorCode.FILE_COUNT_EXCEEDED,
            ApiErrorCode.UNSUPPORTED_FILE_TYPE,
            ApiErrorCode.SUPPORTING_DOCUMENT_REQUIRED
    );

    private static final Map<ApiErrorCode, String> MESSAGES = Map.of(
            ApiErrorCode.FILE_EMPTY, "Uploaded files must not be empty.",
            ApiErrorCode.FILE_TOO_LARGE, "One or more files exceed the allowed size.",
            ApiErrorCode.FILE_COUNT_EXCEEDED, "You can upload a maximum of three files for this action.",
            ApiErrorCode.UNSUPPORTED_FILE_TYPE, "Only PDF, JPG, and PNG files are allowed.",
            ApiErrorCode.SUPPORTING_DOCUMENT_REQUIRED, "Add at least one supporting document."
    );

    private final ApiErrorCode code;

    public FileValidationException(ApiErrorCode code) {
        super(messageFor(code));
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    private static String messageFor(ApiErrorCode code) {
        if (code == null || !ALLOWED_CODES.contains(code)) {
            throw new IllegalArgumentException("Unsupported file-validation error code.");
        }
        return MESSAGES.get(code);
    }
}
