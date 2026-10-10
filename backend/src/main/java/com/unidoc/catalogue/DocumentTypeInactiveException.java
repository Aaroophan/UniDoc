package com.unidoc.catalogue;

public class DocumentTypeInactiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentTypeInactiveException() {
        super("This document type is not available for new requests.");
    }
}
