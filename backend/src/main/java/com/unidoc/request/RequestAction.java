package com.unidoc.request;

/** Documented workflow actions; availability is advisory, never authorization. */
public enum RequestAction {
    START_REVIEW,
    REQUEST_INFORMATION,
    APPROVE,
    REJECT,
    RESPOND,
    START_PROCESSING,
    UPLOAD_ISSUED_DOCUMENT,
    READY_FOR_COLLECTION,
    COMPLETE_COLLECTION,
    DOWNLOAD_ISSUED_DOCUMENT
}
