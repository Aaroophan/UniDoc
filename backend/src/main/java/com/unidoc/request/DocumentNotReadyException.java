package com.unidoc.request;

public class DocumentNotReadyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentNotReadyException() {
        super("The document is not ready for download.");
    }
}
