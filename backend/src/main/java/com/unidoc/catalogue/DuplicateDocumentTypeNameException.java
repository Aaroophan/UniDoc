package com.unidoc.catalogue;

public class DuplicateDocumentTypeNameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateDocumentTypeNameException() {
        super("A document type already uses this name.");
    }
}
