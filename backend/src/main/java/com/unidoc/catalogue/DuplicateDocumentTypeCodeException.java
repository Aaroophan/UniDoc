package com.unidoc.catalogue;

public class DuplicateDocumentTypeCodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateDocumentTypeCodeException() {
        super("A document type already uses this code.");
    }
}
